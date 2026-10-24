package dev.laranja.jobhunter.sourcing.internal;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.github.tomakehurst.wiremock.client.WireMock;
import dev.laranja.jobhunter.IntegrationTest;
import dev.laranja.jobhunter.jobs.JobCatalog;
import dev.laranja.jobhunter.jobs.JobOrigin;
import dev.laranja.jobhunter.jobs.RawJob;
import dev.laranja.jobhunter.sourcing.internal.LinkedInDescriptions.Outcome;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

class LinkedInDescriptionsIntegrationTests extends IntegrationTest {

	private static final String PATH = "/jobs-guest/jobs/api/jobPosting/";

	@Autowired
	private WireMockServer google;

	@Autowired
	private LinkedInDescriptions descriptions;

	@Autowired
	private JobCatalog catalog;

	@BeforeEach
	void reset() {
		this.google.resetAll();
		this.descriptions.clearPause();
	}

	@Test
	void englishDescriptionFillsTheJobAndMovesItToInternational() throws Exception {
		// Título em português e local no Brasil: sem descrição, entra como nacional.
		long jobId = linkedInJob("4011", "Desenvolvedor Java");
		assertThat(scope(jobId)).isEqualTo("NACIONAL");
		stubPage("4011", aResponse().withStatus(200).withBody(LinkedInJobPageTest.PAGE));

		assertThat(this.descriptions.fetchNext()).isEqualTo(Outcome.OK);

		assertThat(this.jdbc.sql("select raw_description from job where id = :id").param("id", jobId)
			.query(String.class)
			.single()).contains("We are looking for a Senior Java Developer");
		assertThat(scope(jobId)).isEqualTo("INTERNACIONAL");
		assertThat(this.jdbc.sql("select status from linkedin_description_fetch where job_id = :id").param("id", jobId)
			.query(String.class)
			.single()).isEqualTo("OK");
		// a análise é refeita com a descrição
		await(() -> this.jdbc.sql("""
				select count(*) from job_analysis
				where job_id = :id and (required_skills::text || nice_to_have::text) ilike '%kafka%'
				""")
			.param("id", jobId)
			.query(Long.class)
			.single() > 0);
		assertThat(this.descriptions.fetchNext()).isEqualTo(Outcome.FILA_VAZIA);
	}

	@Test
	void removedJobIsNotTriedAgainAndABlockPausesTheQueue() throws Exception {
		long removed = linkedInJob("4021", "Desenvolvedor Java Pleno");
		stubPage("4021", aResponse().withStatus(404));
		assertThat(this.descriptions.fetchNext()).isEqualTo(Outcome.REMOVIDA);

		linkedInJob("4022", "Desenvolvedor Java Sênior");
		stubPage("4022", aResponse().withStatus(429));
		assertThat(this.descriptions.fetchNext()).isEqualTo(Outcome.BLOQUEADA);
		// durante a pausa nem consulta o LinkedIn
		assertThat(this.descriptions.fetchNext()).isEqualTo(Outcome.PAUSADA);
		this.google.verify(1, WireMock.getRequestedFor(urlPathEqualTo(PATH + "4022")));

		// o bloqueio não conta como tentativa; a vaga removida saiu da fila
		this.descriptions.clearPause();
		stubPage("4022", aResponse().withStatus(200).withBody(LinkedInJobPageTest.PAGE));
		assertThat(this.descriptions.fetchNext()).isEqualTo(Outcome.OK);
		assertThat(this.descriptions.fetchNext()).isEqualTo(Outcome.FILA_VAZIA);
		this.google.verify(1, WireMock.getRequestedFor(urlPathEqualTo(PATH + "4021")));
		assertThat(this.jdbc.sql("select raw_description from job where id = :id").param("id", removed)
			.query(String.class)
			.optional()
			.orElse(null)).isNull();
	}

	private long linkedInJob(String externalId, String title) {
		return this.catalog.ingest(new RawJob(JobOrigin.LINKEDIN_ALERTA, externalId, title, "Empresa " + externalId,
				"Brasil (Remoto)", null, null, "https://www.linkedin.com/jobs/view/" + externalId + "/", null))
			.jobId();
	}

	private String scope(long jobId) {
		return this.jdbc.sql("select scope from job where id = :id").param("id", jobId).query(String.class).single();
	}

	private void stubPage(String externalId, ResponseDefinitionBuilder response) {
		this.google.stubFor(WireMock.get(urlPathEqualTo(PATH + externalId)).willReturn(response));
	}

}
