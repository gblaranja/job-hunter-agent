package dev.laranja.jobhunter.jobs;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

import dev.laranja.jobhunter.companies.CompanyDirectory;
import dev.laranja.jobhunter.jobs.internal.Job;
import dev.laranja.jobhunter.jobs.internal.JobFingerprint;
import dev.laranja.jobhunter.jobs.internal.JobRepository;
import dev.laranja.jobhunter.shared.ConflictException;
import dev.laranja.jobhunter.shared.NotFoundException;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Catálogo de vagas: a única porta de escrita da tabela {@code job}. */
@Service
@Transactional(readOnly = true)
public class JobCatalog {

	private final JobRepository jobs;

	private final CompanyDirectory companies;

	private final JdbcClient jdbc;

	private final ApplicationEventPublisher events;

	private final Clock clock;

	JobCatalog(JobRepository jobs, CompanyDirectory companies, JdbcClient jdbc, ApplicationEventPublisher events,
			Clock clock) {
		this.jobs = jobs;
		this.companies = companies;
		this.jdbc = jdbc;
		this.events = events;
		this.clock = clock;
	}

	public JobView get(long id) {
		return this.jobs.findById(id).map(Job::toView).orElseThrow(() -> NotFoundException.of("Vaga", id));
	}

	@Transactional
	public JobView registerManual(ManualJobCommand command) {
		String fingerprint = JobFingerprint.of(command.company(), command.title(), command.location());
		this.jobs.findByFingerprint(fingerprint).ifPresent(existing -> {
			throw new ConflictException("Essa vaga já está cadastrada.", existing.getId());
		});
		long companyId = this.companies.resolveOrRegister(command.company());
		Job job = this.jobs.save(Job.manual(command, fingerprint, companyId, this.clock.instant()));
		this.events.publishEvent(new JobCollected(job.getId(), job.getOrigin()));
		return job.toView();
	}

	/**
	 * Registra uma vaga coletada. É idempotente: a mesma vaga (pelo id na fonte ou pelo fingerprint) só
	 * atualiza o "visto por último", e só uma vaga nova publica {@link JobCollected}.
	 */
	@Transactional
	public IngestResult ingest(RawJob raw) {
		Instant now = this.clock.instant();
		Optional<Long> sameExternal = (raw.externalId() == null) ? Optional.empty()
				: this.jdbc.sql("select id from job where source = :source and external_id = :externalId")
					.param("source", raw.origin().name())
					.param("externalId", raw.externalId())
					.query(Long.class)
					.optional();
		if (sameExternal.isPresent()) {
			touch(sameExternal.get(), now);
			return new IngestResult(sameExternal.get(), false);
		}

		long companyId = this.companies.resolveOrRegister(raw.company());
		// xmax = 0 só na linha recém-inserida; no conflito, a linha existente é atualizada e devolvida.
		IngestResult result = this.jdbc.sql("""
				insert into job (source, external_id, fingerprint, title, company, location, work_model, salary_range,
				                 url, raw_description, collected_at, company_id, first_seen_at, last_seen_at)
				values (:source, :externalId, :fingerprint, :title, :company, :location, :workModel, :salaryRange,
				        :url, :description, :now, :companyId, :now, :now)
				on conflict (fingerprint) do update set last_seen_at = excluded.last_seen_at
				returning id, (xmax = 0) as created
				""")
			.param("source", raw.origin().name())
			.param("externalId", raw.externalId())
			.param("fingerprint", JobFingerprint.of(raw.company(), raw.title(), raw.location()))
			.param("title", truncate(raw.title().strip(), 300))
			.param("company", truncate(raw.company().strip(), 200))
			.param("location", truncate(raw.location(), 200))
			.param("workModel", raw.resolvedWorkModel().name())
			.param("salaryRange", truncate(raw.salaryRange(), 100))
			.param("url", truncate(raw.url(), 2000))
			.param("description", raw.description())
			.param("now", Timestamp.from(now))
			.param("companyId", companyId)
			.query((rs, row) -> new IngestResult(rs.getLong("id"), rs.getBoolean("created")))
			.single();
		if (result.created()) {
			this.events.publishEvent(new JobCollected(result.jobId(), raw.origin()));
		}
		return result;
	}

	private void touch(long jobId, Instant now) {
		this.jdbc.sql("update job set last_seen_at = :now where id = :id")
			.param("now", Timestamp.from(now))
			.param("id", jobId)
			.update();
	}

	private static String truncate(String value, int max) {
		if (value == null || value.isBlank()) {
			return null;
		}
		String stripped = value.strip();
		return (stripped.length() <= max) ? stripped : stripped.substring(0, max);
	}

}
