package dev.laranja.jobhunter.tracking;

import java.time.Instant;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.jayway.jsonpath.JsonPath;
import dev.laranja.jobhunter.GmailStub;
import dev.laranja.jobhunter.IntegrationTest;
import dev.laranja.jobhunter.TestFiles;
import dev.laranja.jobhunter.inbox.Mailbox;
import dev.laranja.jobhunter.shared.security.SecretCipher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** O e-mail "recebemos sua candidatura" confirma, sugere ou registra a candidatura sem digitar. */
class ConfirmationEmailIntegrationTests extends IntegrationTest {

	@Autowired
	private WireMockServer googleApi;

	@Autowired
	private SecretCipher cipher;

	@Autowired
	private Mailbox mailbox;

	private long resume;

	@BeforeEach
	void connect() throws Exception {
		this.googleApi.resetAll();
		GmailStub.connect(this.jdbc, this.cipher, this.googleApi);
		this.resume = uploadResume(TestFiles.pdf("confirmacao"), "cv.pdf", "Backend");
	}

	@Test
	void aConfirmationForAnAppliedApplicationIsLinkedAndLeavesTheReviewQueue() throws Exception {
		long jobId = createJob("Desenvolvedor Java", "Banco Exemplo", "Java");
		long applicationId = startApplication(jobId, "{\"status\": \"APLICADA\", \"resumeVersionId\": %d}".formatted(this.resume));
		long emailId = receive("talentos@bancoexemplo.com.br", "Recebemos sua candidatura");

		this.mailbox.classifyPending(10);

		await(() -> applicationOf(emailId) != null);
		assertThat(applicationOf(emailId)).isEqualTo(applicationId);
		assertThat(this.jdbc.sql("select needs_review from email_message where id = :id").param("id", emailId)
			.query(Boolean.class).single()).isFalse();
	}

	@Test
	void aConfirmationForAnInterestSuggestsMovingToApplied() throws Exception {
		long jobId = createJob("Desenvolvedor Java", "Banco Exemplo", "Java");
		long applicationId = startApplication(jobId, "{\"status\": \"INTERESSE\"}");
		long emailId = receive("talentos@bancoexemplo.com.br", "Recebemos sua candidatura");

		this.mailbox.classifyPending(10);

		await(() -> applicationOf(emailId) != null);
		this.mvc.perform(get("/api/emails").with(owner()))
			.andExpect(jsonPath("$[0].aiClassification").value("CONFIRMACAO_CANDIDATURA"))
			.andExpect(jsonPath("$[0].needsReview").value(true))
			.andExpect(jsonPath("$[0].applicationId").value(applicationId))
			.andExpect(jsonPath("$[0].applicationStatus").value("INTERESSE"));
	}

	@Test
	void aConfirmationWithoutApplicationBecomesAnAppliedApplicationWithTheEmailDate() throws Exception {
		long emailId = receive("vagas@fintechexemplo.com", "Obrigado por se candidatar");
		Instant received = Instant.parse("2026-10-20T13:00:00Z");
		this.jdbc.sql("update email_message set received_at = :at where id = :id")
			.param("at", java.sql.Timestamp.from(received))
			.param("id", emailId)
			.update();
		this.mailbox.classifyPending(10);
		awaitListeners();
		assertThat(applicationOf(emailId)).isNull();

		String response = this.mvc.perform(post("/api/applications/from-email/{id}", emailId).with(owner())
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"resumeVersionId": %d, "title": "Engenheira de Software", "company": "Fintech Exemplo"}
					""".formatted(this.resume)))
			.andExpect(status().isCreated())
			.andReturn()
			.getResponse()
			.getContentAsString();
		long applicationId = ((Number) JsonPath.read(response, "$.id")).longValue();

		assertThat(applicationOf(emailId)).isEqualTo(applicationId);
		assertThat(this.jdbc.sql("select status from application where id = :id").param("id", applicationId)
			.query(String.class).single()).isEqualTo("APLICADA");
		assertThat(this.jdbc.sql("select applied_at from application where id = :id").param("id", applicationId)
			.query(java.sql.Timestamp.class).single().toInstant()).isEqualTo(received);
		assertThat(this.jdbc.sql("select source from job").query(String.class).single()).isEqualTo("MANUAL");

		this.mvc.perform(post("/api/applications/from-email/{id}", emailId).with(owner())
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"resumeVersionId\": %d}".formatted(this.resume)))
			.andExpect(status().isConflict());
	}

	private long receive(String sender, String subject) {
		return GmailStub.receive(this.jdbc, this.googleApi, "g-" + System.nanoTime(), sender, subject,
				subject + ". Nosso time vai analisar o seu perfil.");
	}

	private Long applicationOf(long emailId) {
		return this.jdbc.sql("select application_id from email_message where id = :id")
			.param("id", emailId)
			.query((rs, row) -> java.util.Optional.ofNullable(rs.getObject(1, Long.class)))
			.single()
			.orElse(null);
	}

}
