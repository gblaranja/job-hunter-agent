package dev.laranja.jobhunter.sourcing.internal;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import dev.laranja.jobhunter.jobs.JobOrigin;
import dev.laranja.jobhunter.jobs.RawJob;
import dev.laranja.jobhunter.jobs.WorkModel;
import org.junit.jupiter.api.Test;

import org.springframework.core.io.ClassPathResource;

import static org.assertj.core.api.Assertions.assertThat;

class AlertParsersTest {

	@Test
	void linkedInAlertBecomesOneJobPerCardWithCanonicalUrl() throws IOException {
		List<RawJob> jobs = new LinkedInAlertParser().parse(fixture("linkedin-alerta.html"));

		assertThat(jobs).extracting(RawJob::externalId).containsExactly("4012345678", "4087654321", "4011112222");
		RawJob first = jobs.getFirst();
		assertThat(first.origin()).isEqualTo(JobOrigin.LINKEDIN_ALERTA);
		assertThat(first.title()).isEqualTo("Desenvolvedor Java Sênior");
		assertThat(first.company()).isEqualTo("Banco Exemplo");
		assertThat(first.location()).isEqualTo("São Paulo, SP (Remoto)");
		assertThat(first.salaryRange()).isEqualTo("R$ 15.000/mês - R$ 18.000/mês");
		assertThat(first.url()).isEqualTo("https://www.linkedin.com/jobs/view/4012345678/");
		assertThat(first.resolvedWorkModel()).isEqualTo(WorkModel.REMOTO);

		RawJob second = jobs.get(1);
		assertThat(second.company()).isEqualTo("Fintech de Crédito");
		assertThat(second.location()).isEqualTo("Ribeirão Preto, SP (Híbrido)");
		assertThat(second.resolvedWorkModel()).isEqualTo(WorkModel.HIBRIDO);

		assertThat(jobs.get(2).company()).isEqualTo("Consultoria Tech Ltda.");
	}

	@Test
	void linkedInCardWrappedInALinkStillSeparatesTitleCompanyAndLocation() throws IOException {
		List<RawJob> jobs = new LinkedInAlertParser().parse(fixture("linkedin-alerta-card-link.html"));

		assertThat(jobs).extracting(RawJob::externalId).containsExactly("4099990001", "4099990002");
		assertThat(jobs.getFirst()).satisfies(job -> {
			assertThat(job.title()).isEqualTo("Backend Developer - Java / Spring Boot / Kafka");
			assertThat(job.company()).isEqualTo("Empresa Remota");
			assertThat(job.location()).isEqualTo("Brasil (Remoto)");
		});
		assertThat(jobs.get(1)).satisfies(job -> {
			assertThat(job.title()).isEqualTo("Pessoa Engenheira de Software Sênior");
			assertThat(job.company()).isEqualTo("Fintech Exemplo");
			assertThat(job.location()).isEqualTo("São Paulo, SP (Híbrido)");
		});
	}

	@Test
	void gupyAlertUsesTheCareerPageSubdomainWhenTheCompanyIsMissing() throws IOException {
		List<RawJob> jobs = new GupyAlertParser().parse(fixture("gupy-alerta.html"));

		assertThat(jobs).hasSize(2);
		assertThat(jobs.getFirst()).satisfies(job -> {
			assertThat(job.origin()).isEqualTo(JobOrigin.GUPY_ALERTA);
			assertThat(job.externalId()).isEqualTo("7654321");
			assertThat(job.title()).isEqualTo("Pessoa Desenvolvedora Back-end Java Sênior");
			assertThat(job.company()).isEqualTo("Banco Exemplo S.A.");
			assertThat(job.url()).isEqualTo("https://banco-exemplo.gupy.io/jobs/7654321");
			assertThat(job.resolvedWorkModel()).isEqualTo(WorkModel.REMOTO);
		});
		assertThat(jobs.get(1).company()).isEqualTo("Cooperativa Credito");
	}

	@Test
	void parsersOnlyClaimTheirSenders() {
		assertThat(new LinkedInAlertParser().supports("jobalerts-noreply@linkedin.com")).isTrue();
		assertThat(new LinkedInAlertParser().supports("noreply@gupy.io")).isFalse();
		assertThat(new GupyAlertParser().supports("alertas@email.gupy.io")).isTrue();
	}

	@Test
	void unknownLayoutYieldsNoJobsInsteadOfGarbage() {
		assertThat(new LinkedInAlertParser().parse("<html><body><p>Seu resumo semanal</p></body></html>")).isEmpty();
	}

	static String fixture(String name) throws IOException {
		return new ClassPathResource("fixtures/alerts/" + name).getContentAsString(StandardCharsets.UTF_8);
	}

}
