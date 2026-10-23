package dev.laranja.jobhunter.sourcing;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import dev.laranja.jobhunter.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;

import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@TestPropertySource(properties = { "jobhunter.sourcing.remotive.enabled=true", "jobhunter.sourcing.github.enabled=true" })
class BroadSourcesIntegrationTests extends IntegrationTest {

	@Autowired
	private WireMockServer google;

	@BeforeEach
	void reset() {
		this.google.resetAll();
	}

	@Test
	void broadSourcesOnlyKeepJobsWithProfileKeywords() throws Exception {
		this.google.stubFor(WireMock.get(urlPathEqualTo("/api/remote-jobs")).willReturn(okJson("""
				{"jobs": [
				  {"id": 1, "url": "https://remotive.com/1", "title": "Backend Engineer", "company_name": "Remota",
				   "candidate_required_location": "Worldwide", "salary": "",
				   "description": "<p>Java 21 and Spring Boot.</p>"},
				  {"id": 2, "url": "https://remotive.com/2", "title": "Product Designer", "company_name": "Remota",
				   "candidate_required_location": "Worldwide", "description": "<p>Figma.</p>"}]}
				""")));
		this.google.stubFor(WireMock.get(urlPathEqualTo("/repos/backend-br/vagas/issues")).willReturn(okJson("""
				[{"number": 9001, "title": "[Remoto] Desenvolvedor Java Pleno @ Fintech X",
				  "html_url": "https://github.com/backend-br/vagas/issues/9001", "body": "Java, Spring",
				  "labels": [{"name": "CLT"}, {"name": "Remoto"}]},
				 {"number": 9002, "title": "[São Paulo] Dev Ruby na Loja Y",
				  "html_url": "https://github.com/backend-br/vagas/issues/9002", "body": "Rails"},
				 {"number": 9003, "title": "Ajuste no README", "html_url": "https://github.com/backend-br/vagas/pull/9003",
				  "body": "Java", "pull_request": {"url": "x"}}]
				""")));

		this.mvc.perform(post("/api/admin/collect").with(owner())).andExpect(status().isOk());

		assertThat(this.jdbc.sql("select title from job where source = 'REMOTIVE'").query(String.class).list())
			.containsExactly("Backend Engineer");
		assertThat(this.jdbc.sql("select title || ' | ' || company || ' | ' || work_model from job where source = 'GITHUB'")
			.query(String.class)
			.list()).containsExactly("Desenvolvedor Java Pleno | Fintech X | REMOTO");
	}

}
