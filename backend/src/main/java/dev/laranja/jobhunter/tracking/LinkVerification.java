package dev.laranja.jobhunter.tracking;

import java.sql.Timestamp;
import java.sql.Types;
import java.time.Clock;
import java.util.List;

import dev.laranja.jobhunter.shared.web.LinkVerifier;
import dev.laranja.jobhunter.shared.web.LinkVerifier.Result;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Verificação semanal dos links das vagas aplicadas ainda em aberto. Uma vaga só vira REMOVIDA depois de duas
 * verificações seguidas sem a página no ar, para um erro passageiro do site não gerar alarme falso.
 */
@Service
public class LinkVerification {

	static final int FAILURES_TO_REMOVE = 2;

	private static final Logger log = LoggerFactory.getLogger(LinkVerification.class);

	private final LinkVerifier verifier;

	private final JdbcClient jdbc;

	private final Clock clock;

	LinkVerification(LinkVerifier verifier, JdbcClient jdbc, Clock clock) {
		this.verifier = verifier;
		this.jdbc = jdbc;
		this.clock = clock;
	}

	@Scheduled(cron = "${jobhunter.tracking.link-check-cron:0 0 8 * * MON}", zone = "America/Sao_Paulo")
	@SchedulerLock(name = "verify-links", lockAtMostFor = "PT1H")
	void weekly() {
		Summary summary = verifyOpenApplications();
		log.info("Links verificados: {} ativos, {} removidos, {} indeterminados.", summary.active(), summary.removed(),
				summary.indeterminate());
	}

	public Summary verifyOpenApplications() {
		record Target(long id, String url, int failures) {
		}
		List<Target> targets = this.jdbc.sql("""
				select s.id, s.job_url as url, s.link_consecutive_failures as failures
				from job_snapshot s join application a on a.id = s.application_id
				where s.job_url is not null and a.status not in ('OFERTA', 'RECUSADA', 'DESISTI', 'ENCERRADA')
				order by s.link_checked_at nulls first
				""")
			.query(Target.class)
			.list();
		int active = 0;
		int removed = 0;
		int indeterminate = 0;
		for (Target target : targets) {
			Result result = this.verifier.check(target.url());
			int failures = (result == Result.REMOVIDO) ? target.failures() + 1 : (result == Result.ATIVO) ? 0 : target.failures();
			String status = (result == Result.ATIVO) ? "ATIVO"
					: (failures >= FAILURES_TO_REMOVE) ? "REMOVIDO" : null;
			this.jdbc.sql("""
					update job_snapshot
					set link_status = coalesce(:status, link_status), link_consecutive_failures = :failures,
					    link_checked_at = :now
					where id = :id
					""")
				.param("status", status, Types.VARCHAR)
				.param("failures", failures)
				.param("now", Timestamp.from(this.clock.instant()))
				.param("id", target.id())
				.update();
			switch (result) {
				case ATIVO -> active++;
				case REMOVIDO -> removed++;
				case INDETERMINADO -> indeterminate++;
			}
		}
		return new Summary(targets.size(), active, removed, indeterminate);
	}

	/** @param removed links que responderam como removidos nesta verificação (viram REMOVIDO na segunda vez seguida) */
	public record Summary(int checked, int active, int removed, int indeterminate) {
	}

}
