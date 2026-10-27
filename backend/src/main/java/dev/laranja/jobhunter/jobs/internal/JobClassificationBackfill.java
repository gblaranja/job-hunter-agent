package dev.laranja.jobhunter.jobs.internal;

import java.util.List;

import org.jspecify.annotations.Nullable;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Classifica família de cargo e condições de contratação no exterior das vagas anteriores às colunas ou de uma
 * versão antiga das regras ({@link JobClassification#VERSION}). Idempotente.
 */
@Component
class JobClassificationBackfill implements ApplicationRunner {

	private final JdbcClient jdbc;

	JobClassificationBackfill(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	@Override
	@Transactional
	public void run(ApplicationArguments args) {
		record Pending(long id, String title, @Nullable String location, @Nullable String rawDescription) {
		}
		List<Pending> pending = this.jdbc.sql("""
				select id, title, location, raw_description from job
				where classification_version is distinct from :version
				""")
			.param("version", JobClassification.VERSION)
			.query(Pending.class)
			.list();
		for (Pending job : pending) {
			JobClassification classification = JobClassification.of(job.title(), job.location(), job.rawDescription());
			this.jdbc.sql("""
					update job set job_family = :family, international_eligibility = :eligibility,
					    required_timezone = :timezone, contractor = :contractor, classification_version = :version
					where id = :id
					""")
				.param("family", classification.familyName())
				.param("eligibility", classification.international().eligibility().name())
				.param("timezone", classification.international().timezone())
				.param("contractor", classification.international().contractor())
				.param("version", JobClassification.VERSION)
				.param("id", job.id())
				.update();
		}
	}

}
