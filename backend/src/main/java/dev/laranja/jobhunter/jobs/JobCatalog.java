package dev.laranja.jobhunter.jobs;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import dev.laranja.jobhunter.companies.CompanyDirectory;
import dev.laranja.jobhunter.jobs.internal.Job;
import dev.laranja.jobhunter.jobs.internal.JobDuplicates;
import dev.laranja.jobhunter.jobs.internal.JobFingerprint;
import dev.laranja.jobhunter.jobs.internal.JobRepository;
import dev.laranja.jobhunter.jobs.internal.JobScopeDetector;
import dev.laranja.jobhunter.jobs.internal.LegitimacyAssessor;
import dev.laranja.jobhunter.shared.ConflictException;
import dev.laranja.jobhunter.shared.NotFoundException;
import org.jspecify.annotations.Nullable;

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

	private final JobDuplicates duplicates;

	private final LegitimacyAssessor legitimacy;

	private final Clock clock;

	JobCatalog(JobRepository jobs, CompanyDirectory companies, JdbcClient jdbc, ApplicationEventPublisher events,
			JobDuplicates duplicates, LegitimacyAssessor legitimacy, Clock clock) {
		this.jobs = jobs;
		this.companies = companies;
		this.jdbc = jdbc;
		this.events = events;
		this.duplicates = duplicates;
		this.legitimacy = legitimacy;
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
		this.jobs.flush();
		linkRepost(job.getId(), companyId, command.title(), this.clock.instant());
		this.legitimacy.assess(job.getId());
		this.events.publishEvent(new JobCollected(job.getId(), job.getOrigin()));
		return job.toView();
	}

	/**
	 * Registra uma vaga coletada. É idempotente: a mesma vaga (pelo id na fonte ou pelo fingerprint) só
	 * atualiza o "visto por último", e só uma vaga nova publica {@link JobCollected}. A mesma vaga vinda de outra
	 * fonte vira cópia da canônica (ver {@link JobDuplicates}); uma cópia nova não é analisada.
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
			// Fontes que buscam a descrição depois (detalhe da SmartRecruiters) completam a vaga que entrou sem ela.
			if (raw.description() != null && !raw.description().isBlank()) {
				attachDescription(sameExternal.get(), raw.description());
			}
			return new IngestResult(sameExternal.get(), false);
		}

		long companyId = this.companies.resolveOrRegister(raw.company());
		// xmax = 0 só na linha recém-inserida; no conflito, a linha existente é atualizada e devolvida.
		IngestResult result = this.jdbc.sql("""
				insert into job (source, external_id, fingerprint, title, company, location, work_model, scope,
				                 salary_range, url, raw_description, collected_at, company_id, first_seen_at,
				                 last_seen_at)
				values (:source, :externalId, :fingerprint, :title, :company, :location, :workModel, :scope,
				        :salaryRange, :url, :description, :now, :companyId, :now, :now)
				on conflict (fingerprint) do update set last_seen_at = excluded.last_seen_at, closed_at = null
				returning id, (xmax = 0) as created
				""")
			.param("source", raw.origin().name())
			.param("externalId", raw.externalId())
			.param("fingerprint", JobFingerprint.of(raw.company(), raw.title(), raw.location()))
			.param("title", truncate(raw.title().strip(), 300))
			.param("company", truncate(raw.company().strip(), 200))
			.param("location", truncate(raw.location(), 200))
			.param("workModel", raw.resolvedWorkModel().name())
			.param("scope", JobScopeDetector.detect(raw.title(), raw.location(), raw.description()).name())
			.param("salaryRange", truncate(raw.salaryRange(), 100))
			.param("url", truncate(raw.url(), 2000))
			.param("description", raw.description())
			.param("now", Timestamp.from(now))
			.param("companyId", companyId)
			.query((rs, row) -> new IngestResult(rs.getLong("id"), rs.getBoolean("created")))
			.single();
		if (result.created()) {
			linkRepost(result.jobId(), companyId, raw.title(), now);
			boolean duplicate = deduplicate(result.jobId(), now);
			this.legitimacy.assess(result.jobId());
			if (!duplicate) {
				this.events.publishEvent(new JobCollected(result.jobId(), raw.origin()));
			}
		}
		return result;
	}

	/** Ids na fonte das vagas da empresa que já têm descrição: a coleta não precisa buscar o detalhe delas de novo. */
	public Set<String> describedExternalIds(JobOrigin origin, long companyId) {
		return Set.copyOf(this.jdbc.sql("""
				select external_id from job
				where source = :source and company_id = :companyId and external_id is not null
				  and raw_description is not null
				""")
			.param("source", origin.name())
			.param("companyId", companyId)
			.query(String.class)
			.list());
	}

	/**
	 * Liga a vaga recém-criada à mesma vaga de outra fonte. A canônica sem descrição herda a da cópia.
	 * @return se a vaga nova ficou como cópia
	 */
	private boolean deduplicate(long jobId, Instant now) {
		Optional<JobDuplicates.Match> match = this.duplicates.find(jobId, now);
		if (match.isEmpty()) {
			return false;
		}
		long canonical = match.get().canonicalId();
		long duplicate = match.get().duplicateId();
		this.duplicates.link(duplicate, canonical);
		String description = this.jdbc.sql("select raw_description from job where id = :id")
			.param("id", duplicate)
			.query(String.class)
			.optional()
			.orElse(null);
		if (description != null && !description.isBlank()) {
			if (canonical == jobId) {
				// a vaga nova ainda não foi analisada: a descrição entra antes do JobCollected, sem reanálise
				this.jdbc.sql("update job set raw_description = :description where id = :id and raw_description is null")
					.param("description", description)
					.param("id", jobId)
					.update();
			}
			else {
				attachDescription(canonical, description);
			}
		}
		if (canonical == jobId) {
			this.legitimacy.assess(duplicate);
		}
		return canonical != jobId;
	}

	/**
	 * Separa uma vaga marcada como cópia de outra: ela volta para a caixa e, se nunca foi analisada, é analisada agora.
	 */
	@Transactional
	public void separateDuplicate(long jobId) {
		JobView job = get(jobId);
		if (!this.duplicates.unlink(jobId)) {
			return;
		}
		this.legitimacy.assess(jobId);
		boolean analyzed = this.jdbc.sql("select count(*) from job_analysis where job_id = :id")
			.param("id", jobId)
			.query(Long.class)
			.single() > 0;
		if (!analyzed) {
			this.events.publishEvent(new JobCollected(jobId, job.origin()));
		}
	}

	/**
	 * Fecha as vagas da empresa, vindas de uma fonte que lista tudo (ATS), que não apareceram na listagem atual.
	 * @return quantas vagas foram fechadas
	 */
	@Transactional
	public int closeMissing(JobOrigin origin, long companyId, Collection<String> listedExternalIds) {
		Instant now = this.clock.instant();
		List<Long> closed = this.jdbc.sql("""
				update job set closed_at = :now
				where source = :source and company_id = :companyId and closed_at is null
				  and not (external_id = any(string_to_array(:listed, chr(31))))
				returning id
				""")
			.param("now", Timestamp.from(now))
			.param("source", origin.name())
			.param("companyId", companyId)
			.param("listed", String.join(String.valueOf((char) 31), listedExternalIds))
			.query(Long.class)
			.list();
		// a mesma vaga vinda de um alerta fecha junto
		this.duplicates.closeLinked(closed, now);
		return closed.size();
	}

	/**
	 * Corrige a classificação automática: a vaga muda de seção na caixa de vagas e a escolha fica travada contra
	 * reclassificações.
	 */
	@Transactional
	public void changeScope(long jobId, JobScope scope) {
		int updated = this.jdbc.sql("update job set scope = :scope, scope_locked = true where id = :id")
			.param("scope", scope.name())
			.param("id", jobId)
			.update();
		if (updated == 0) {
			throw NotFoundException.of("Vaga", jobId);
		}
	}

	/**
	 * Completa a descrição de uma vaga que entrou sem ela. Com o texto em mãos, a classificação nacional/internacional
	 * é refeita (se você não a corrigiu à mão) e {@link JobDescriptionAdded} dispara uma nova análise.
	 * @return se a descrição foi gravada; falso quando a vaga já tinha descrição ou não existe
	 */
	@Transactional
	public boolean attachDescription(long jobId, String description) {
		record Current(String title, @Nullable String location) {
		}
		Optional<Current> current = this.jdbc
			.sql("select title, location from job where id = :id and raw_description is null for update")
			.param("id", jobId)
			.query(Current.class)
			.optional();
		if (current.isEmpty() || description.isBlank()) {
			return false;
		}
		this.jdbc.sql("""
				update job set raw_description = :description,
				    scope = case when scope_locked then scope else :scope end
				where id = :id
				""")
			.param("description", description.strip())
			.param("scope", JobScopeDetector.detect(current.get().title(), current.get().location(), description).name())
			.param("id", jobId)
			.update();
		this.legitimacy.assess(jobId);
		this.events.publishEvent(new JobDescriptionAdded(jobId));
		return true;
	}

	/** Você viu que a vaga não aceita mais candidaturas: fecha agora, se ainda estava aberta. */
	@Transactional
	public void markClosed(long jobId) {
		this.jdbc.sql("update job set closed_at = coalesce(closed_at, :now) where id = :id")
			.param("now", Timestamp.from(this.clock.instant()))
			.param("id", jobId)
			.update();
	}

	/**
	 * Vaga republicada: mesma empresa e título parecido (similaridade trigram acima de 0,8) nos últimos 90 dias.
	 * Prefere a anterior já fechada, que é o caso típico de vaga reaberta.
	 */
	private void linkRepost(long jobId, long companyId, String title, Instant now) {
		this.jdbc.sql("""
				update job set repost_of_job_id = (
				    select previous.id from job previous
				    where previous.company_id = :companyId and previous.id <> :jobId
				      and previous.first_seen_at >= :since
				      and similarity(lower(previous.title), lower(:title)) > 0.8
				    order by (previous.closed_at is not null) desc, previous.first_seen_at desc
				    limit 1)
				where id = :jobId
				""")
			.param("companyId", companyId)
			.param("jobId", jobId)
			.param("since", Timestamp.from(now.minus(Duration.ofDays(90))))
			.param("title", title)
			.update();
	}

	private void touch(long jobId, Instant now) {
		this.jdbc.sql("update job set last_seen_at = :now, closed_at = null where id = :id")
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
