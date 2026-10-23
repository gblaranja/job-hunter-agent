package dev.laranja.jobhunter.tracking;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import dev.laranja.jobhunter.AnthropicStubConfiguration;
import dev.laranja.jobhunter.GmailStub;
import dev.laranja.jobhunter.IntegrationTest;
import dev.laranja.jobhunter.TestFiles;
import dev.laranja.jobhunter.inbox.Mailbox;
import dev.laranja.jobhunter.shared.security.SecretCipher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;

import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Com a IA: confiança alta e candidatura única movem o card; a correção desfaz com um evento compensatório. */
@Import(AnthropicStubConfiguration.class)
class EmailResponsesLlmIntegrationTests extends IntegrationTest {

	@Autowired
	private WireMockServer googleApi;

	@Autowired
	private WireMockServer anthropicApi;

	@Autowired
	private SecretCipher cipher;

	@Autowired
	private Mailbox mailbox;

	private long applicationId;

	@BeforeEach
	void applyToBancoExemplo() throws Exception {
		this.googleApi.resetAll();
		this.anthropicApi.resetAll();
		GmailStub.connect(this.jdbc, this.cipher, this.googleApi);
		long resume = uploadResume(TestFiles.pdf("llm-respostas"), "cv.pdf", "Backend");
		long jobId = createJob("Desenvolvedor Java", "Banco Exemplo", "Java e Spring");
		this.applicationId = startApplication(jobId, "{\"status\": \"APLICADA\", \"resumeVersionId\": %d}".formatted(resume));
	}

	@Test
	void confidentInviteMovesTheCardAndACorrectionCompensatesIt() throws Exception {
		classifyAs("Convite para entrevista", "CONVITE_ENTREVISTA", 0.93);
		long emailId = GmailStub.receive(this.jdbc, this.googleApi, "g-2", "Ana <ana@consultoria-rh.com>",
				"Convite para entrevista", "Olá! O Banco Exemplo gostaria de conversar com você na quinta.");

		this.mailbox.classifyPending(10);
		await(() -> "ENTREVISTA".equals(applicationStatus()));

		assertThat(count("select count(*) from application_event where origin = 'EMAIL' and email_message_id = " + emailId))
			.isEqualTo(1);
		assertThat(count("select count(*) from email_message where needs_review")).isZero();

		this.mvc.perform(patch("/api/emails/{id}/classification", emailId).with(owner())
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"classification\": \"OUTRO\", \"applicationId\": %d}".formatted(this.applicationId)))
			.andExpect(status().isNoContent());

		await(() -> "APLICADA".equals(applicationStatus()));
		// O histórico ganha o evento compensatório; nada é apagado.
		assertThat(count("select count(*) from application_event where application_id = " + this.applicationId)).isEqualTo(3);
		assertThat(count("select count(*) from email_message where classified_by = 'USUARIO'")).isEqualTo(1);
	}

	@Test
	void lowConfidenceOnlySuggestsTheApplication() throws Exception {
		classifyAs("Retorno", "CONVITE_ENTREVISTA", 0.5);
		GmailStub.receive(this.jdbc, this.googleApi, "g-3", "rh@bancoexemplo.com.br", "Retorno", "Podemos conversar?");

		this.mailbox.classifyPending(10);
		await(() -> count("select count(*) from email_message where application_id is not null") == 1);

		assertThat(applicationStatus()).isEqualTo("APLICADA");
		assertThat(count("select count(*) from email_message where needs_review")).isEqualTo(1);
	}

	private void classifyAs(String subject, String classification, double confidence) {
		this.anthropicApi.stubFor(WireMock.post("/v1/messages").withRequestBody(containing("classifica e-mails"))
			.willReturn(okJson(AnthropicStubConfiguration.messageWith("""
					{"classification": "%s", "company": "Banco Exemplo", "jobTitle": "Desenvolvedor Java",
					 "confidence": %s, "rationale": "%s"}
					""".formatted(classification, confidence, subject)))));
	}

	private String applicationStatus() {
		return this.jdbc.sql("select status from application where id = :id").param("id", this.applicationId)
			.query(String.class).single();
	}

	private long count(String sql) {
		return this.jdbc.sql(sql).query(Long.class).single();
	}

}
