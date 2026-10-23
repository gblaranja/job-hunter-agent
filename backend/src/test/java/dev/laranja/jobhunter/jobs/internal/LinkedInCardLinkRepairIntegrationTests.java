package dev.laranja.jobhunter.jobs.internal;

import dev.laranja.jobhunter.IntegrationTest;
import dev.laranja.jobhunter.jobs.JobCatalog;
import dev.laranja.jobhunter.jobs.JobOrigin;
import dev.laranja.jobhunter.jobs.RawJob;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;

import static org.assertj.core.api.Assertions.assertThat;

class LinkedInCardLinkRepairIntegrationTests extends IntegrationTest {

	@Autowired
	private JobCatalog catalog;

	@Autowired
	private LinkedInCardLinkRepair repair;

	@Test
	void fixesJobsReadFromTheWholeCardLinkAndDropsTheCompaniesNamedAfterTheTitle() {
		long broken = ingest("4100000001",
				"Backend Developer - Java / Spring Boot / Kafka Empresa Remota · Brasil (Remoto) Candidatura simplificada",
				"Backend Developer - Java / Spring Boot / Kafka", "Empresa Remota · Brasil (Remoto)");
		long healthy = ingest("4100000002", "Desenvolvedor Java Sênior", "Banco Exemplo", "São Paulo, SP (Remoto)");
		// A mesma vaga já tinha entrado certa por outro alerta: o fingerprint correto já está ocupado.
		long alreadyCorrect = ingest("4100000003", "Pessoa Engenheira de Software", "Fintech Exemplo", "Brasil (Remoto)");
		long duplicate = ingest("4100000004", "Pessoa Engenheira de Software Fintech Exemplo · Brasil (Remoto)",
				"Pessoa Engenheira de Software", "Fintech Exemplo · Brasil (Remoto)");

		this.repair.run(new DefaultApplicationArguments());
		this.repair.run(new DefaultApplicationArguments());

		assertThat(job(broken)).containsExactly("Backend Developer - Java / Spring Boot / Kafka", "Empresa Remota",
				"Brasil (Remoto)", "Empresa Remota");
		assertThat(job(healthy)).containsExactly("Desenvolvedor Java Sênior", "Banco Exemplo", "São Paulo, SP (Remoto)",
				"Banco Exemplo");
		assertThat(job(duplicate)).containsExactly("Pessoa Engenheira de Software", "Fintech Exemplo", "Brasil (Remoto)",
				"Fintech Exemplo");
		assertThat(fingerprint(alreadyCorrect)).isNotEqualTo(fingerprint(duplicate));
		assertThat(this.jdbc.sql("select name from company order by name").query(String.class).list())
			.containsExactly("Banco Exemplo", "Empresa Remota", "Fintech Exemplo");
	}

	private long ingest(String externalId, String title, String company, String location) {
		return this.catalog
			.ingest(new RawJob(JobOrigin.LINKEDIN_ALERTA, externalId, title, company, location, null, null,
					"https://www.linkedin.com/jobs/view/" + externalId + "/", null))
			.jobId();
	}

	private Object[] job(long id) {
		return this.jdbc.sql("""
				select job.title, job.company, job.location, company.name
				from job join company on company.id = job.company_id where job.id = :id
				""")
			.param("id", id)
			.query((rs, row) -> new Object[] { rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4) })
			.single();
	}

	private String fingerprint(long id) {
		return this.jdbc.sql("select fingerprint from job where id = :id").param("id", id).query(String.class).single();
	}

}
