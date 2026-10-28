package dev.laranja.jobhunter.shared.ai;

import java.sql.Timestamp;
import java.time.Instant;

import com.github.tomakehurst.wiremock.WireMockServer;
import dev.laranja.jobhunter.AnthropicStubConfiguration;
import dev.laranja.jobhunter.IntegrationTest;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

@Import(AnthropicStubConfiguration.class)
class LlmBudgetIntegrationTests extends IntegrationTest {

	@Autowired
	private WireMockServer anthropicApi;

	@Test
	void exhaustedBudgetKeepsTheAnalysisDeterministicAndThePanelWarns() throws Exception {
		this.anthropicApi.resetAll();
		// 40 milhões de tokens de entrada no Haiku: US$ 4, acima do orçamento padrão de US$ 3 da extração.
		this.jdbc.sql("""
				insert into llm_usage (task, model, input_tokens, output_tokens, created_at)
				values ('extract-requirements', 'claude-haiku-5-5', 40000000, 0, :now)
				""").param("now", Timestamp.from(Instant.now())).update();

		long jobId = createJob("Desenvolvedor Java Sênior", "Fintech", """
				Buscamos pessoa desenvolvedora backend para o motor de crédito. Requisitos: Java 21, Spring Boot, Kafka
				e AWS. Diferenciais: Kubernetes. Contratação CLT, remoto.
				""");
		await(() -> this.jdbc.sql("select count(*) from job_analysis where status = 'PENDENTE' and job_id = :id")
			.param("id", jobId)
			.query(Long.class)
			.single() == 1);

		this.anthropicApi.verify(0, postRequestedFor(urlPathEqualTo("/v1/messages"))
			.withRequestBody(containing("analista de vagas")));
		this.mvc.perform(get("/api/jobs/{id}", jobId).with(owner()))
			.andExpect(jsonPath("$.analysis.requiredSkills[0]").value("Java"))
			.andExpect(jsonPath("$.analysis.lastError", containsString("Orçamento mensal")));
		this.mvc.perform(get("/api/insights/budget").with(owner()))
			.andExpect(jsonPath("$[?(@.task == 'extract-requirements')].exhausted").value(true))
			.andExpect(jsonPath("$[?(@.task == 'extract-requirements')].spentUsd").value(4.0))
			.andExpect(jsonPath("$[?(@.task == 'match-score')].exhausted").value(false));
	}

}
