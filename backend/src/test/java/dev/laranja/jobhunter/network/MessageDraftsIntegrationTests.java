package dev.laranja.jobhunter.network;

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

/** Com a Claude API: o rascunho usa a vaga, o contato e o currículo, e o convite respeita os 300 caracteres. */
@Import(AnthropicStubConfiguration.class)
class MessageDraftsIntegrationTests extends IntegrationTest {

	@Autowired
	private WireMockServer anthropicApi;

	@BeforeEach
	void stubClaude() {
		this.anthropicApi.resetAll();
		this.anthropicApi.stubFor(WireMock.post("/v1/messages").atPriority(10).willReturn(aResponse().withStatus(500)));
		this.anthropicApi.stubFor(WireMock.post("/v1/messages")
			.atPriority(1)
			.withRequestBody(containing("rascunhos de mensagens"))
			.willReturn(okJson(messageWith("""
					{"text": "Olá, Ana! Vi a vaga de Desenvolvedor Java no Banco Exemplo. Trabalho com Java e Spring Boot. Você toparia me indicar? [seu nome]",
					 "linkedinNote": "%s"}
					""".formatted("Olá, Ana! ".repeat(40))))));
	}

	@Test
	void draftsAReferralRequestWithTheContactAndLimitsTheLinkedInNote() throws Exception {
		uploadResume(TestFiles.pdfWithText("Ana Lima Desenvolvedora Java Spring Boot"), "cv.pdf", "Backend");
		long jobId = createJob("Desenvolvedor Java", "Banco Exemplo", "Buscamos pessoa com Java e Spring Boot.");
		long contactId = contact();
		awaitListeners();

		String response = this.mvc.perform(post("/api/jobs/{id}/drafts", jobId).with(owner())
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"kind\": \"PEDIDO_INDICACAO\", \"contactId\": %d}".formatted(contactId)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.source").value("IA"))
			.andExpect(jsonPath("$.language").value("pt"))
			.andExpect(jsonPath("$.text").value(org.hamcrest.Matchers.startsWith("Olá, Ana!")))
			.andReturn()
			.getResponse()
			.getContentAsString();

		assertThat(((String) JsonPath.read(response, "$.linkedinNote")).length()).isLessThanOrEqualTo(300);
		this.anthropicApi.verify(postRequestedFor(urlPathEqualTo("/v1/messages"))
			.withRequestBody(containing("Ana Lima · RECRUTADOR"))
			.withRequestBody(containing("Tipo: PEDIDO_INDICACAO")));
	}

	private long contact() throws Exception {
		String response = this.mvc.perform(post("/api/contacts").with(owner())
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"name\": \"Ana Lima\", \"role\": \"RECRUTADOR\", \"company\": \"Banco Exemplo\"}"))
			.andExpect(status().isCreated())
			.andReturn()
			.getResponse()
			.getContentAsString();
		return ((Number) JsonPath.read(response, "$.id")).longValue();
	}

}
