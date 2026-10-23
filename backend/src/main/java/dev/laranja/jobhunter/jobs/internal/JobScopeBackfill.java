package dev.laranja.jobhunter.jobs.internal;

import java.util.List;

import org.jspecify.annotations.Nullable;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Classifica como nacionais ou internacionais as vagas cadastradas antes da coluna {@code scope}. Idempotente. */
@Component
class JobScopeBackfill implements ApplicationRunner {

	private final JdbcClient jdbc;

	JobScopeBackfill(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	@Override
	@Transactional
	public void run(ApplicationArguments args) {
		record Pending(long id, @Nullable String rawDescription) {
		}
		List<Pending> pending = this.jdbc.sql("select id, raw_description from job where scope is null")
			.query(Pending.class)
			.list();
		for (Pending job : pending) {
			this.jdbc.sql("update job set scope = :scope where id = :id")
				.param("scope", JobScopeDetector.detect(job.rawDescription()).name())
				.param("id", job.id())
				.update();
		}
	}

}
