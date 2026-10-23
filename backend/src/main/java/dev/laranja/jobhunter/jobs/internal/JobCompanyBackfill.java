package dev.laranja.jobhunter.jobs.internal;

import java.util.List;

import dev.laranja.jobhunter.companies.CompanyDirectory;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Liga à empresa as vagas cadastradas antes do cadastro de empresas existir. Idempotente. */
@Component
class JobCompanyBackfill implements ApplicationRunner {

	private final JdbcClient jdbc;

	private final CompanyDirectory companies;

	JobCompanyBackfill(JdbcClient jdbc, CompanyDirectory companies) {
		this.jdbc = jdbc;
		this.companies = companies;
	}

	@Override
	@Transactional
	public void run(ApplicationArguments args) {
		record Pending(long id, String company) {
		}
		List<Pending> pending = this.jdbc.sql("select id, company from job where company_id is null")
			.query(Pending.class)
			.list();
		for (Pending job : pending) {
			this.jdbc.sql("update job set company_id = :companyId where id = :id")
				.param("companyId", this.companies.resolveOrRegister(job.company()))
				.param("id", job.id())
				.update();
		}
	}

}
