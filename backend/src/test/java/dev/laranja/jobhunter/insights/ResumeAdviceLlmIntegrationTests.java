package dev.laranja.jobhunter.insights;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import dev.laranja.jobhunter.AnthropicStubConfiguration;
import dev.laranja.jobhunter.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static dev.laranja.jobhunter.AnthropicStubConfiguration.messageWith;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Import(AnthropicStubConfiguration.class)
class ResumeAdviceLlmIntegrationTests extends IntegrationTest {

	@Autowired
	private WireMockServer anthropicApi;

	@BeforeEach
	void reset() {
		this.anthropicApi.resetAll();
	}

	@Test
	void theLargerModelReadsTheResumeAndTheWeekNumbers() throws Exception {
		InsightFixtures.seed(this.jdbc);
		this.anthropicApi.stubFor(WireMock.post("/v1/messages").withRequestBody(containing("recrutadora técnica"))
			.willReturn(okJson(messageWith("""
					{"gaps": [{"skill": "Kafka", "evidence": "8 das 12 vagas pedem Kafka.",
					           "suggestion": "Cite a fila de pagamentos que você construiu."}],
					 "bullets": [{"original": "Construí APIs REST com Spring Boot para pagamentos.",
					              "rewritten": "Construí APIs REST em Spring Boot que processam [X] pagamentos por dia.",
					              "reason": "Mostra escala."}],
					 "linkedinKeywords": ["Java", "Spring Boot", "Pagamentos"],
					 "diagnosis": "CURRICULO", "diagnosisExplanation": "Vagas aderentes, pouca conversão.",
					 "gupyProfile": {"skills": "Java, Spring Boot, Kafka", "experiences": [
					     {"role": "Desenvolvedora", "company": "Banco Azul", "description": "APIs de crédito em Java."}]},
					 "linkedinProfile": {"headline": "Backend Java Sênior | Spring Boot | Kafka",
					     "about": "Desenvolvo sistemas de crédito.", "skills": ["Java", "Kafka"]}}
					"""))));

		this.mvc.perform(post("/api/insights/generate").with(owner()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.recommendations.source").value("IA"))
			.andExpect(jsonPath("$.recommendations.bullets[0].rewritten").value(
					"Construí APIs REST em Spring Boot que processam [X] pagamentos por dia."))
			.andExpect(jsonPath("$.recommendations.diagnosis").value("CURRICULO"))
			.andExpect(jsonPath("$.recommendations.gupyProfile.experiences[0].company").value("Banco Azul"))
			.andExpect(jsonPath("$.recommendations.linkedinProfile.headline").value("Backend Java Sênior | Spring Boot | Kafka"))
			.andExpect(jsonPath("$.recommendations.profileChanges[0]").value("Primeira versão dos textos dos perfis."))
			.andExpect(jsonPath("$.modelVersion").value("claude-opus-5-5|resume-advice@v2"));

		this.anthropicApi.verify(postRequestedFor(urlPathEqualTo("/v1/messages"))
			.withRequestBody(containing("\"claude-opus-5-5\""))
			.withRequestBody(containing("\"adaptive\""))
			.withRequestBody(containing("Construí APIs REST com Spring Boot"))
			.withRequestBody(containing("Kafka")));
		assertThat(this.jdbc.sql("select count(*) from llm_usage where task = 'resume-advice'").query(Long.class).single())
			.isEqualTo(1);
	}

	@Test
	void llmFailureKeepsTheRuleBasedAdvice() throws Exception {
		InsightFixtures.seed(this.jdbc);
		this.anthropicApi.stubFor(WireMock.post("/v1/messages").willReturn(aResponse().withStatus(500)));

		this.mvc.perform(post("/api/insights/generate").with(owner()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.recommendations.source").value("REGRAS"));
	}

}
