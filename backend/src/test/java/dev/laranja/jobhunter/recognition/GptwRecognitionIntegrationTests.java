package dev.laranja.jobhunter.recognition;

import java.time.Year;
import java.time.ZoneId;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import dev.laranja.jobhunter.AnthropicStubConfiguration;
import dev.laranja.jobhunter.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static dev.laranja.jobhunter.AnthropicStubConfiguration.messageWith;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Import(AnthropicStubConfiguration.class)
@TestPropertySource(properties = "jobhunter.recognition.search.api-key=test-search-key")
class GptwRecognitionIntegrationTests extends IntegrationTest {

	private static final int YEAR = Year.now(ZoneId.of("America/Sao_Paulo")).getValue();

	@Autowired
	private WireMockServer googleApi;

	@Autowired
	private WireMockServer anthropicApi;

	@BeforeEach
	void reset() {
		this.googleApi.resetAll();
		this.anthropicApi.resetAll();
	}

	@Test
	void aNewCompanyWithAnOpenJobGetsTheBadgeAndTheCacheAvoidsASecondSearch() throws Exception {
		String url = "https://gptw.com.br/empresa/alfa-pagamentos";
		stubSearch(url, "Alfa Pagamentos é uma das premiadas do GPTW TI " + YEAR);
		stubVerdict("""
				{"kind": "RANKING_TI", "year": %d, "rankingName": "GPTW TI %d", "rankingPosition": 12, "validUntil": "",
				 "evidenceUrl": "%s", "evidenceExcerpt": "Alfa Pagamentos é uma das premiadas", "matchedName": "Alfa Pagamentos",
				 "confidence": 0.93, "rationale": "Página oficial do GPTW."}
				""".formatted(YEAR, YEAR, url));

		long jobId = createJob("Desenvolvedor Java", "Alfa Pagamentos", "Java e Spring");
		long companyId = companyOf(jobId);
		await(() -> recognitions() == 1);

		this.mvc.perform(get("/api/companies").param("gptw", "true").with(owner()))
			.andExpect(jsonPath("$[*].name", contains("Alfa Pagamentos")))
			.andExpect(jsonPath("$[0].gptwLevel").value("RANKING_TI"))
			.andExpect(jsonPath("$[0].gptwYear").value(YEAR));
		this.mvc.perform(get("/api/jobs").param("inbox", "true").with(owner()))
			.andExpect(jsonPath("$.items[0].gptwLevel").value("RANKING_TI"));
		this.googleApi.verify(2, postRequestedFor(urlPathEqualTo("/search")).withHeader("Authorization",
				WireMock.equalTo("Bearer test-search-key")));
		this.googleApi.verify(1, postRequestedFor(urlPathEqualTo("/search")).withRequestBody(containing("include_domains")));
		this.anthropicApi.verify(postRequestedFor(urlPathEqualTo("/v1/messages"))
			.withRequestBody(containing("claude-haiku-5-5"))
			.withRequestBody(containing(url)));

		// Verificar de novo usa o cache: nenhuma busca paga a mais, e o mesmo selo só renova a data.
		this.mvc.perform(post("/api/companies/{id}/recognitions/check", companyId).with(owner()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.level").value("RANKING_TI"));
		this.googleApi.verify(2, postRequestedFor(urlPathEqualTo("/search")));
		assertThat(recognitions()).isEqualTo(1);
		this.mvc.perform(get("/api/companies/{id}/recognitions", companyId).with(owner()))
			.andExpect(jsonPath("$[0].evidenceUrl").value(url))
			.andExpect(jsonPath("$[0].rankingPosition").value(12))
			.andExpect(jsonPath("$[0].verifiedBy").value("IA"));
		this.mvc.perform(get("/api/recognitions/status").with(owner()))
			.andExpect(jsonPath("$.enabled").value(true))
			.andExpect(jsonPath("$.searchesThisMonth").value(2));
	}

	@Test
	void aHomonymWaitsForYourConfirmation() throws Exception {
		this.jdbc.sql("insert into company (name, normalized_name) values ('Stone Pagamentos', 'stone pagamentos')").update();
		String url = "https://gptw.com.br/empresa/stone";
		stubSearch(url, "Stone conquista a certificação GPTW " + YEAR);
		stubVerdict("""
				{"kind": "CERTIFICACAO", "year": %d, "rankingName": "", "rankingPosition": 0, "validUntil": "",
				 "evidenceUrl": "%s", "evidenceExcerpt": "Stone conquista a certificação", "matchedName": "Stone",
				 "confidence": 0.95, "rationale": "Página oficial."}
				""".formatted(YEAR, url));

		createJob("Engenheiro de Software", "Stone", "Java");
		await(() -> count("select count(*) from notification where type = 'SELO_REVISAR'") == 1);

		this.mvc.perform(get("/api/companies").param("gptw", "true").with(owner())).andExpect(jsonPath("$", empty()));
		String queue = this.mvc.perform(get("/api/recognitions/review").with(owner()))
			.andExpect(jsonPath("$[0].companyName").value("Stone"))
			.andExpect(jsonPath("$[0].level").value("CERTIFICADA"))
			.andExpect(jsonPath("$[0].homonyms", contains("Stone Pagamentos")))
			.andReturn()
			.getResponse()
			.getContentAsString();
		Integer id = com.jayway.jsonpath.JsonPath.read(queue, "$[0].id");

		this.mvc.perform(post("/api/recognitions/{id}/confirm", id).with(owner()))
			.andExpect(jsonPath("$.reviewStatus").value("CONFIRMADO"))
			.andExpect(jsonPath("$.verifiedBy").value("USUARIO"));
		this.mvc.perform(get("/api/companies").param("gptw", "true").with(owner()))
			.andExpect(jsonPath("$[*].name", contains("Stone")));
		this.mvc.perform(get("/api/recognitions/review").with(owner())).andExpect(jsonPath("$", empty()));
	}

	@Test
	void withoutTheYearTheResultIsNotFound() throws Exception {
		String url = "https://exemplo.com/noticia";
		stubSearch(url, "Gama é uma ótima empresa para trabalhar");
		stubVerdict("""
				{"kind": "CERTIFICACAO", "year": 0, "rankingName": "", "rankingPosition": 0, "validUntil": "",
				 "evidenceUrl": "%s", "evidenceExcerpt": "", "matchedName": "Gama", "confidence": 0.7,
				 "rationale": "Notícia sem ano."}
				""".formatted(url));

		createJob("Dev Java", "Gama Sistemas", "Java");
		await(() -> recognitions() == 1);

		assertThat(this.jdbc.sql("select level from company_recognition").query(String.class).single())
			.isEqualTo("NAO_ENCONTRADA");
		assertThat(count("select count(*) from company_gptw")).isZero();
	}

	@Test
	void theMonthlyLimitStopsPaidSearches() throws Exception {
		this.jdbc.sql("""
				insert into web_search_cache (provider, query_hash, query, response, fetched_at)
				select 'tavily', lpad(n::text, 64, '0'), 'consulta ' || n, '[]', now() from generate_series(1, 200) n
				""").update();
		this.jdbc.sql("insert into company (name, normalized_name, watch_status) values ('Delta', 'delta', 'ALVO')").update();
		long delta = this.jdbc.sql("select id from company where name = 'Delta'").query(Long.class).single();

		this.mvc.perform(post("/api/companies/{id}/recognitions/check", delta).with(owner()))
			.andExpect(status().isUnprocessableEntity());
		this.googleApi.verify(0, postRequestedFor(urlPathEqualTo("/search")));
	}

	private void stubSearch(String url, String content) {
		this.googleApi.stubFor(WireMock.post(urlPathEqualTo("/search")).willReturn(okJson("""
				{"query": "q", "results": [{"title": "Certificação GPTW", "url": "%s", "content": "%s", "score": 0.9}]}
				""".formatted(url, content))));
	}

	private void stubVerdict(String json) {
		this.anthropicApi.stubFor(WireMock.post("/v1/messages")
			.withRequestBody(containing("Você verifica se uma empresa"))
			.willReturn(okJson(messageWith(json))));
	}

	private long companyOf(long jobId) {
		return this.jdbc.sql("select company_id from job where id = :id").param("id", jobId).query(Long.class).single();
	}

	private long recognitions() {
		return count("select count(*) from company_recognition");
	}

	private long count(String sql) {
		return this.jdbc.sql(sql).query(Long.class).single();
	}

}
