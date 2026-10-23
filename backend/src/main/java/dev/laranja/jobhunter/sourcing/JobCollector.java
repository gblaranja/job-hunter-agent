package dev.laranja.jobhunter.sourcing;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

import dev.laranja.jobhunter.inbox.Mailbox;
import dev.laranja.jobhunter.jobs.IngestResult;
import dev.laranja.jobhunter.jobs.JobCatalog;
import dev.laranja.jobhunter.profile.JobFilter;
import dev.laranja.jobhunter.profile.SearchProfiles;
import dev.laranja.jobhunter.shared.ConflictException;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.json.JsonMapper;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

/**
 * Executa a coleta: sincroniza o Gmail, passa por cada fonte, aplica o filtro dos perfis de busca e grava as
 * vagas no catálogo. Cada execução fica registrada em {@code collection_run} com as contagens por fonte.
 */
@Service
public class JobCollector {

	private static final Logger log = LoggerFactory.getLogger(JobCollector.class);

	private final List<JobSource> sources;

	private final Mailbox mailbox;

	private final SearchProfiles profiles;

	private final JobCatalog catalog;

	private final JdbcClient jdbc;

	private final JsonMapper json;

	private final MeterRegistry meters;

	private final Clock clock;

	private final ReentrantLock running = new ReentrantLock();

	JobCollector(List<JobSource> sources, Mailbox mailbox, SearchProfiles profiles, JobCatalog catalog,
			JdbcClient jdbc, JsonMapper json, MeterRegistry meters, Clock clock) {
		this.sources = sources;
		this.mailbox = mailbox;
		this.profiles = profiles;
		this.catalog = catalog;
		this.jdbc = jdbc;
		this.json = json;
		this.meters = meters;
		this.clock = clock;
	}

	public long collect(Trigger trigger) {
		if (!this.running.tryLock()) {
			throw new ConflictException("Já existe uma coleta em andamento.", null);
		}
		try {
			return run(trigger);
		}
		finally {
			this.running.unlock();
		}
	}

	private long run(Trigger trigger) {
		long runId = this.jdbc.sql("""
				insert into collection_run (trigger, status, started_at) values (:trigger, 'RODANDO', :now) returning id
				""")
			.param("trigger", trigger.name())
			.param("now", Timestamp.from(this.clock.instant()))
			.query(Long.class)
			.single();
		Map<String, SourceStats> stats = new LinkedHashMap<>();
		try {
			if (this.mailbox.connected()) {
				Mailbox.SyncResult sync = this.mailbox.sync();
				log.info("Gmail sincronizado: {} e-mails novos, {} alertas.", sync.newMessages(), sync.newAlerts());
			}
			JobFilter filter = this.profiles.activeFilter();
			for (JobSource source : this.sources) {
				if (!source.available()) {
					continue;
				}
				SourceStats sourceStats = new SourceStats();
				stats.put(source.id(), sourceStats);
				source.collect(raw -> {
					try {
						if (filter.rejectionReason(raw.title(), raw.description(), raw.location(),
								raw.resolvedWorkModel().name()).isPresent()) {
							sourceStats.excluded++;
							count(source, "excluded");
							return;
						}
						IngestResult result = this.catalog.ingest(raw);
						if (result.created()) {
							sourceStats.created++;
						}
						else {
							sourceStats.duplicate++;
						}
						count(source, result.created() ? "new" : "duplicate");
					}
					catch (RuntimeException ex) {
						log.warn("Falha ao gravar a vaga \"{}\" de {}: {}", raw.title(), source.id(), ex.getMessage());
						sourceStats.errors++;
						count(source, "error");
					}
				});
			}
			finish(runId, "CONCLUIDA", stats, null);
		}
		catch (RuntimeException ex) {
			log.error("Coleta {} falhou", runId, ex);
			finish(runId, "FALHOU", stats, ex.getMessage());
		}
		return runId;
	}

	private void count(JobSource source, String result) {
		this.meters.counter("jobhunter.jobs.collected", "source", source.id(), "result", result).increment();
	}

	private void finish(long runId, String status, Map<String, SourceStats> stats, String error) {
		Instant now = this.clock.instant();
		this.jdbc.sql("""
				update collection_run set status = :status, finished_at = :now, stats = cast(:stats as jsonb), error = :error
				where id = :id
				""")
			.param("status", status)
			.param("now", Timestamp.from(now))
			.param("stats", this.json.writeValueAsString(stats))
			.param("error", error)
			.param("id", runId)
			.update();
	}

	public enum Trigger {

		AGENDADA, MANUAL

	}

	/** Contagens de uma fonte numa coleta; serializado no jsonb de {@code collection_run}. */
	public static final class SourceStats {

		public int created;

		public int duplicate;

		public int excluded;

		public int errors;

	}

}
