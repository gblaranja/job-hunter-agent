package dev.laranja.jobhunter.interviews;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.jayway.jsonpath.JsonPath;
import dev.laranja.jobhunter.AnthropicStubConfiguration;
import dev.laranja.jobhunter.IntegrationTest;
import dev.laranja.jobhunter.TestFiles;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;

import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static dev.laranja.jobhunter.AnthropicStubConfiguration.messageWith;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Import(AnthropicStubConfiguration.class)
class InterviewsLlmIntegrationTests extends IntegrationTest {

	@Autowired
	private WireMockServer anthropicApi;

	@BeforeEach
	void stubClaude() {
		this.anthropicApi.resetAll();
		// Análise da vaga e do currículo: qualquer outra chamada responde vazio e cai nas regras.
		this.anthropicApi.stubFor(WireMock.post("/v1/messages").atPriority(10).willReturn(okJson(messageWith("{}"))));
		this.anthropicApi.stubFor(WireMock.post("/v1/messages").withRequestBody(containing("prepara uma pessoa"))
			.willReturn(okJson(messageWith("""
					{"questions": [
					   {"question": "Como você garante entrega única no Kafka?", "reason": "Requisito obrigatório.",
					    "storyId": 1, "answerOutline": "Conte a migração do motor."},
					   {"question": "Fale de Kubernetes.", "reason": "Desejável.", "storyId": 99,
					    "answerOutline": "Diga o que conhece."}],
					 "technicalTopics": ["Kafka"], "companyFacts": ["Banco digital."],
					 "questionsToAsk": ["Como é o time?"], "pointsToClarify": ["Faixa salarial."]}
					"""))));
		this.anthropicApi.stubFor(WireMock.post("/v1/messages").withRequestBody(containing("formato STAR"))
			.willReturn(okJson(messageWith("""
					{"situation": "O motor de crédito caía no pico.", "task": "", "action": "Movi o cálculo para eventos.",
					 "result": "Reduzi as quedas em 90%.", "reflection": "", "notes": "Complete a tarefa."}
					"""))));
		this.anthropicApi.stubFor(WireMock.post("/v1/messages").withRequestBody(containing("responder uma proposta"))
			.willReturn(okJson(messageWith("""
					{"message": "Obrigado! O mercado paga R$ 25.000 por mês para esta posição."}
					"""))));
	}

	@Test
	void prepUsesTheModelAndKeepsOnlyExistingStories() throws Exception {
		this.mvc.perform(post("/api/stories").with(owner())
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"title": "Motor de crédito", "situation": "Caía.", "action": "Kafka.", "result": "Parou.",
					 "skills": ["Kafka"]}
					"""))
			.andExpect(status().isCreated());
		long jobId = createJob("Desenvolvedor Java", "Banco Alfa", "Java, Spring Boot e Kafka. Remoto.");
		long resumeId = uploadResume(TestFiles.pdf("llm"), "cv.pdf", "Backend");
		long applicationId = startApplication(jobId, """
				{"status": "APLICADA", "resumeVersionId": %d}
				""".formatted(resumeId));
		this.mvc.perform(patch("/api/applications/{id}/status", applicationId).with(owner())
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"status\": \"ENTREVISTA\"}"))
			.andExpect(status().isNoContent());
		await(() -> this.jdbc.sql("select count(*) from interview_prep where status = 'PRONTO'").query(Long.class).single() == 1);

		this.mvc.perform(get("/api/applications/{id}/interview-prep", applicationId).with(owner()))
			.andExpect(jsonPath("$[0].source").value("IA"))
			.andExpect(jsonPath("$[0].modelVersion").value("claude-opus-5-5|prepare-interview@v1"))
			.andExpect(jsonPath("$[0].content.questions[0].storyTitle").value("Motor de crédito"))
			.andExpect(jsonPath("$[0].content.questions[1].storyId").doesNotExist());
		this.anthropicApi.verify(postRequestedFor(urlPathEqualTo("/v1/messages"))
			.withRequestBody(containing("\"claude-opus-5-5\""))
			.withRequestBody(containing("id 1: Motor de crédito [Kafka]")));
	}

	@Test
	void polishedStoryFlagsNumbersYouDidNotWrite() throws Exception {
		this.mvc.perform(post("/api/stories/polish").with(owner())
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"title": "Motor", "situation": "O motor caía.", "action": "Eventos.", "result": "Parou de cair."}
					"""))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.result").value("Reduzi as quedas em 90%."))
			.andExpect(jsonPath("$.reflection").doesNotExist())
			.andExpect(jsonPath("$.warnings[0]").value(containsString("90")));
	}

	@Test
	void negotiationDraftWithAnInventedNumberFallsBackToTheTemplate() throws Exception {
		long jobId = createJob("Desenvolvedor Java", "Banco Alfa", "Java.");
		long applicationId = startApplication(jobId, """
				{"status": "INTERESSE"}
				""");
		String offer = this.mvc.perform(post("/api/applications/{id}/offers", applicationId).with(owner())
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"regime": "CLT", "baseSalary": 15000}
					"""))
			.andExpect(status().isCreated())
			.andReturn()
			.getResponse()
			.getContentAsString();
		long offerId = ((Number) JsonPath.read(offer, "$.id")).longValue();

		this.mvc.perform(post("/api/offers/{id}/negotiation-draft", offerId).with(owner())
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"targetSalary": 18000}
					"""))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.source").value("MODELO"))
			.andExpect(jsonPath("$.text").value(containsString("R$ 18.000 por mês")));
	}

}
