package dev.laranja.jobhunter.jobs;

import java.util.List;

import dev.laranja.jobhunter.IntegrationTest;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Caixa confiável: a mesma vaga de duas fontes aparece uma vez, e as suspeitas ficam escondidas. */
class InboxQualityIntegrationTests extends IntegrationTest {

	private static final String DESCRIPTION = """
			Procuramos pessoa desenvolvedora Java sênior para o time de pagamentos. Você vai desenhar e manter
			microsserviços em Spring Boot, mensageria com Kafka e bancos PostgreSQL, com testes automatizados e
			observabilidade. Requisitos: Java 17+, Spring Boot, Kafka, PostgreSQL, Docker e Kubernetes. Diferenciais:
			experiência com sistemas financeiros, AWS e arquitetura orientada a eventos.""";

	@Autowired
	private JobCatalog catalog;

	@Test
	void alertAndAtsBecomeOneJobWithTheAtsDescriptionAndCloseTogether() throws Exception {
		long alert = this.catalog.ingest(new RawJob(JobOrigin.LINKEDIN_ALERTA, "li-1", "Desenvolvedor Java Sênior",
				"Banco Alfa", "São Paulo, SP", null, null, "https://www.linkedin.com/jobs/view/1", null)).jobId();
		long ats = this.catalog.ingest(new RawJob(JobOrigin.GREENHOUSE, "gh-1", "Desenvolvedor(a) Java Sênior",
				"Banco Alfa", "São Paulo", WorkModel.HIBRIDO, null, "https://boards.greenhouse.io/alfa/jobs/1",
				DESCRIPTION)).jobId();
		awaitListeners();

		// a do ATS é a canônica; a do alerta vira cópia e sai da caixa
		assertThat(this.jdbc.sql("select duplicate_of_job_id from job where id = :id").param("id", alert)
			.query(Long.class).single()).isEqualTo(ats);
		this.mvc.perform(get("/api/jobs").param("inbox", "true").with(owner()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.items[*].id", contains((int) ats)))
			.andExpect(jsonPath("$.items[0].legitimacy").value("ALTA"))
			.andExpect(jsonPath("$.items[0].firstSeenAt").isNotEmpty());
		this.mvc.perform(get("/api/jobs/{id}", ats).with(owner()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.copies[*].origin", contains("LINKEDIN_ALERTA")))
			.andExpect(jsonPath("$.copies[0].canonical").value(false));
		this.mvc.perform(get("/api/jobs/{id}", alert).with(owner()))
			.andExpect(jsonPath("$.legitimacy.duplicateOfJobId").value(ats))
			.andExpect(jsonPath("$.copies[*].id", contains((int) ats)));

		// a vaga some do Greenhouse: a cópia do alerta fecha junto
		long companyId = this.jdbc.sql("select company_id from job where id = :id").param("id", ats)
			.query(Long.class).single();
		assertThat(this.catalog.closeMissing(JobOrigin.GREENHOUSE, companyId, List.of("gh-outra"))).isEqualTo(1);
		assertThat(this.jdbc.sql("select count(*) from job where closed_at is not null").query(Long.class).single())
			.isEqualTo(2);
	}

	@Test
	void atsFirstThenAlertKeepsTheAtsAndSkipsTheCopyAnalysis() throws Exception {
		long ats = this.catalog.ingest(new RawJob(JobOrigin.LEVER, "lv-1", "Backend Engineer Java", "Beta Pay",
				"Remoto", WorkModel.REMOTO, null, "https://jobs.lever.co/beta/lv-1", DESCRIPTION)).jobId();
		awaitListeners();
		long alert = this.catalog.ingest(new RawJob(JobOrigin.GUPY_ALERTA, null, "Backend Engineer (Java/Spring)", "Beta Pay",
				"Remoto", WorkModel.REMOTO, null, "https://betapay.gupy.io/jobs/9", null)).jobId();
		awaitListeners();

		assertThat(this.jdbc.sql("select duplicate_of_job_id from job where id = :id").param("id", alert)
			.query(Long.class).single()).isEqualTo(ats);
		assertThat(this.jdbc.sql("select count(*) from job_analysis where job_id = :id").param("id", alert)
			.query(Long.class).single()).isZero();

		// separar: a cópia volta para a caixa e é analisada
		this.mvc.perform(delete("/api/jobs/{id}/duplicate", alert).with(owner())).andExpect(status().isNoContent());
		awaitListeners();
		this.mvc.perform(get("/api/jobs").param("inbox", "true").with(owner()))
			.andExpect(jsonPath("$.items", hasSize(2)));
		assertThat(this.jdbc.sql("select count(*) from job_analysis where job_id = :id").param("id", alert)
			.query(Long.class).single()).isEqualTo(1);
	}

	@Test
	void differentCitiesOrCompaniesAreNotDuplicates() throws Exception {
		this.catalog.ingest(new RawJob(JobOrigin.GREENHOUSE, "gh-1", "Desenvolvedor Java", "Banco Alfa", "São Paulo",
				WorkModel.PRESENCIAL, null, null, DESCRIPTION));
		this.catalog.ingest(new RawJob(JobOrigin.LINKEDIN_ALERTA, "li-1", "Desenvolvedor Java", "Banco Alfa",
				"Recife, PE", WorkModel.PRESENCIAL, null, null, null));
		this.catalog.ingest(new RawJob(JobOrigin.LINKEDIN_ALERTA, "li-2", "Desenvolvedor Java", "Banco Beta",
				"São Paulo", WorkModel.PRESENCIAL, null, null, null));
		awaitListeners();

		assertThat(this.jdbc.sql("select count(*) from job where duplicate_of_job_id is not null").query(Long.class)
			.single()).isZero();
	}

	@Test
	void inboxHidesSuspiciousJobsUnlessAskedAndMediumLosesPositions() throws Exception {
		long scam = createJob("Digitador home office", "Renda Já",
				"Trabalhe de casa. Para começar, pague a taxa de inscrição de R$ 50 pelo Pix. " + DESCRIPTION);
		long clean = createJob("Desenvolvedor Java", "Acme", DESCRIPTION);
		long old = createJob("Desenvolvedor Java Pleno", "Gama", DESCRIPTION);
		this.jdbc.sql("update job set first_seen_at = now() - interval '40 days' where id = :id").param("id", old)
			.update();
		this.catalog.attachDescription(old, "ignorado: a vaga já tem descrição");
		awaitListeners();
		this.jdbc.sql("update job set legitimacy = 'MEDIA' where id = :id").param("id", old).update();
		// mesma aderência: a vaga MEDIA (e antiga, sem bônus de frescor) fica atrás
		this.jdbc.sql("""
				insert into job_analysis (job_id, status, model_version, match_score, analyzed_at)
				values (:clean, 'DETERMINISTICO', 'teste', 70, now()), (:old, 'DETERMINISTICO', 'teste', 72, now())
				on conflict (job_id) do update set match_score = excluded.match_score
				""").param("clean", clean).param("old", old).update();

		assertThat(this.jdbc.sql("select legitimacy from job where id = :id").param("id", scam).query(String.class)
			.single()).isEqualTo("SUSPEITA");
		this.mvc.perform(get("/api/jobs").param("inbox", "true").with(owner()))
			.andExpect(jsonPath("$.items[*].id", contains((int) clean, (int) old)))
			.andExpect(jsonPath("$.items[0].scoreWithBonus").value(80))
			.andExpect(jsonPath("$.items[1].scoreWithBonus").value(67));
		this.mvc.perform(get("/api/jobs").param("inbox", "true").param("allLegitimacy", "true").with(owner()))
			.andExpect(jsonPath("$.items", hasSize(3)))
			.andExpect(jsonPath("$.items[2].legitimacyReasons[0]").value(
					"Pede pagamento ao candidato (taxa, curso ou material)"));
	}

}
