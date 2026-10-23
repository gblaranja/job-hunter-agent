package dev.laranja.jobhunter.jobs.internal;

import java.sql.Timestamp;
import java.time.Clock;
import java.util.List;

import dev.laranja.jobhunter.shared.web.LinkVerifier;
import dev.laranja.jobhunter.shared.web.LinkVerifier.Result;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Verificação diária dos links das vagas abertas das empresas monitoradas e alvo, vindas de fontes que não listam
 * todas as vagas (alertas, cadastro manual). Duas falhas seguidas fecham a vaga, o que alimenta o "tempo no ar".
 */
@Component
class JobLinkMonitor {

	private static final Logger log = LoggerFactory.getLogger(JobLinkMonitor.class);

	private static final int MAX_PER_RUN = 200;

	private final LinkVerifier verifier;

	private final JdbcClient jdbc;

	private final Clock clock;

	JobLinkMonitor(LinkVerifier verifier, JdbcClient jdbc, Clock clock) {
		this.verifier = verifier;
		this.jdbc = jdbc;
		this.clock = clock;
	}

	@Scheduled(cron = "${jobhunter.jobs.link-check-cron:0 30 6 * * *}", zone = "America/Sao_Paulo")
	@SchedulerLock(name = "monitor-job-links", lockAtMostFor = "PT2H")
	void daily() {
		record Target(long id, String url, int failures) {
		}
		List<Target> targets = this.jdbc.sql("""
				select j.id, j.url, j.link_failures as failures from job j
				join company c on c.id = j.company_id
				where j.closed_at is null and j.url is not null and c.watch_status in ('MONITORADA', 'ALVO')
				  and j.source not in ('GREENHOUSE', 'LEVER', 'ASHBY')
				order by j.last_seen_at
				limit :limit
				""")
			.param("limit", MAX_PER_RUN)
			.query(Target.class)
			.list();
		int closed = 0;
		for (Target target : targets) {
			Result result = this.verifier.check(target.url());
			int failures = (result == Result.REMOVIDO) ? target.failures() + 1 : (result == Result.ATIVO) ? 0 : target.failures();
			boolean close = failures >= 2;
			this.jdbc.sql("""
					update job set link_failures = :failures,
					    closed_at = case when :close then :now else closed_at end,
					    last_seen_at = case when :active then :now else last_seen_at end
					where id = :id
					""")
				.param("failures", failures)
				.param("close", close)
				.param("active", result == Result.ATIVO)
				.param("now", Timestamp.from(this.clock.instant()))
				.param("id", target.id())
				.update();
			closed += close ? 1 : 0;
		}
		log.info("Links de {} vagas monitoradas verificados; {} fechadas.", targets.size(), closed);
	}

}
