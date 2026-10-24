package dev.laranja.jobhunter.sourcing;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import dev.laranja.jobhunter.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AtsCollectionIntegrationTests extends IntegrationTest {

	@Autowired
	private WireMockServer google;

	@BeforeEach
	void reset() {
		this.google.resetAll();
	}

	@Test
	void collectsEveryAtsListingAndClosesJobsThatDisappear() throws Exception {
		stubGreenhouse("""
				{"id": 101, "title": "Senior Java Engineer", "absolute_url": "https://boards.greenhouse.io/acme/jobs/101",
				 "location": {"name": "Remote - Brazil"},
				 "content": "&lt;p&gt;We use &lt;strong&gt;Java&lt;/strong&gt; and Kafka.&lt;/p&gt;&lt;ul&gt;&lt;li&gt;Spring Boot&lt;/li&gt;&lt;/ul&gt;"},
				{"id": 102, "title": "PHP Developer", "absolute_url": "https://boards.greenhouse.io/acme/jobs/102",
				 "location": {"name": "São Paulo"}, "content": "&lt;p&gt;Laravel&lt;/p&gt;"}
				""");
		this.google.stubFor(WireMock.get(urlPathEqualTo("/v0/postings/beta")).willReturn(okJson("""
				[{"id": "lev-1", "text": "Backend Engineer (Kotlin)", "hostedUrl": "https://jobs.lever.co/beta/lev-1",
				  "categories": {"location": "Ribeirão Preto"}, "workplaceType": "hybrid",
				  "descriptionPlain": "Microsserviços em Kotlin.",
				  "lists": [{"text": "Requisitos", "content": "<li>Kotlin</li><li>PostgreSQL</li>"}]}]
				""")));
		this.google.stubFor(WireMock.get(urlPathEqualTo("/posting-api/job-board/gama")).willReturn(okJson("""
				{"jobs": [{"id": "ash-1", "title": "Staff Engineer", "location": "Brasil", "isRemote": true,
				           "jobUrl": "https://jobs.ashbyhq.com/gama/ash-1", "descriptionPlain": "Go e AWS."}]}
				""")));
		// O cadastro confere o slug no ATS: as listagens acima já respondem, e o Greenhouse tem o endpoint do board.
		this.google.stubFor(WireMock.get(urlPathEqualTo("/v1/boards/acme")).willReturn(okJson("{\"name\": \"Acme\"}")));
		track("Acme", "GREENHOUSE", "acme");
		track("Beta", "LEVER", "beta");
		track("Gama", "ASHBY", "gama");

		collect();

		// O ATS entra completo, sem as exclusões do perfil ("PHP"), para as métricas da empresa verem tudo.
		assertThat(count("select count(*) from job where source = 'GREENHOUSE'")).isEqualTo(2);
		assertThat(this.jdbc.sql("select raw_description from job where external_id = '101'").query(String.class).single())
			.contains("We use Java and Kafka.")
			.contains("- Spring Boot")
			.doesNotContain("<p>");
		assertThat(this.jdbc.sql("select work_model from job where external_id = 'lev-1'").query(String.class).single())
			.isEqualTo("HIBRIDO");
		assertThat(this.jdbc.sql("select raw_description from job where external_id = 'lev-1'").query(String.class).single())
			.contains("Requisitos").contains("- PostgreSQL");
		assertThat(this.jdbc.sql("select work_model from job where external_id = 'ash-1'").query(String.class).single())
			.isEqualTo("REMOTO");

		// A vaga 102 saiu da listagem: é fechada. A Lever fora do ar não fecha nada.
		stubGreenhouse("""
				{"id": 101, "title": "Senior Java Engineer", "absolute_url": "https://boards.greenhouse.io/acme/jobs/101",
				 "location": {"name": "Remote - Brazil"}, "content": "Java"}
				""");
		this.google.stubFor(WireMock.get(urlPathEqualTo("/v0/postings/beta")).willReturn(aResponse().withStatus(503)));
		collect();

		assertThat(count("select count(*) from job where external_id = '102' and closed_at is not null")).isEqualTo(1);
		assertThat(count("select count(*) from job where external_id = '101' and closed_at is null")).isEqualTo(1);
		assertThat(count("select count(*) from job where external_id = 'lev-1' and closed_at is null")).isEqualTo(1);

		// Voltou à listagem: reabre.
		stubGreenhouse("""
				{"id": 101, "title": "Senior Java Engineer", "absolute_url": "https://boards.greenhouse.io/acme/jobs/101",
				 "content": "Java"},
				{"id": 102, "title": "PHP Developer", "absolute_url": "https://boards.greenhouse.io/acme/jobs/102",
				 "content": "Laravel"}
				""");
		collect();
		assertThat(count("select count(*) from job where external_id = '102' and closed_at is null")).isEqualTo(1);
	}

	@Test
	void companiesThatAreOnlyDiscoveredAreNotQueried() throws Exception {
		createJob("Dev Java", "Delta", "Java");
		this.jdbc.sql("update company set ats_type = 'GREENHOUSE', ats_slug = 'delta'").update();

		collect();

		assertThat(this.google.findAll(WireMock.getRequestedFor(urlPathEqualTo("/v1/boards/delta/jobs")))).isEmpty();
	}

	private void track(String company, String ats, String slug) throws Exception {
		createJob("Vaga manual em " + company, company, "Java");
		long id = this.jdbc.sql("select id from company where name = :name").param("name", company).query(Long.class).single();
		this.mvc
			.perform(patch("/api/companies/{id}", id).with(owner()).contentType(MediaType.APPLICATION_JSON).content("""
					{"watchStatus": "ALVO", "atsType": "%s", "atsSlug": "%s"}
					""".formatted(ats, slug)))
			.andExpect(status().isOk());
	}

	private void stubGreenhouse(String jobs) {
		this.google.stubFor(WireMock.get(urlPathEqualTo("/v1/boards/acme/jobs")).willReturn(okJson("{\"jobs\": [" + jobs + "]}")));
	}

	private void collect() throws Exception {
		this.mvc.perform(post("/api/admin/collect").with(owner())).andExpect(status().isOk());
	}

	private long count(String sql) {
		return this.jdbc.sql(sql).query(Long.class).single();
	}

}
