package dev.laranja.jobhunter.jobs.internal;

import java.util.List;

import org.jspecify.annotations.Nullable;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Classifica como nacionais ou internacionais as vagas sem classificação: as anteriores à coluna {@code scope} e as
 * que uma migração devolveu para reclassificação quando a regra mudou. Idempotente.
 */
@Component
class JobScopeBackfill implements ApplicationRunner {

	private final JdbcClient jdbc;

	JobScopeBackfill(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	@Override
	@Transactional
	public void run(ApplicationArguments args) {
		record Pending(long id, String title, @Nullable String location, @Nullable String rawDescription) {
		}
		List<Pending> pending = this.jdbc.sql("select id, title, location, raw_description from job where scope is null")
			.query(Pending.class)
			.list();
		for (Pending job : pending) {
			this.jdbc.sql("update job set scope = :scope where id = :id")
				.param("scope", JobScopeDetector.detect(job.title(), job.location(), job.rawDescription()).name())
				.param("id", job.id())
				.update();
		}
	}

}
