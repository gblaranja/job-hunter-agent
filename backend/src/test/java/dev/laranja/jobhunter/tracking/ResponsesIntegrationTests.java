package dev.laranja.jobhunter.tracking;

import com.github.tomakehurst.wiremock.WireMockServer;
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
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Sem IA: as regras classificam com confiança baixa, nada se move sozinho e a sua revisão decide. */
class ResponsesIntegrationTests extends IntegrationTest {

	@Autowired
	private WireMockServer googleApi;

	@Autowired
	private SecretCipher cipher;

	@Autowired
	private Mailbox mailbox;

	private long applicationId;

	@BeforeEach
	void applyToBancoExemplo() throws Exception {
		this.googleApi.resetAll();
		GmailStub.connect(this.jdbc, this.cipher, this.googleApi);
		long resume = uploadResume(TestFiles.pdf("respostas"), "cv.pdf", "Backend");
		long jobId = createJob("Desenvolvedor Java", "Banco Exemplo S.A.", "Java e Spring");
		this.applicationId = startApplication(jobId, "{\"status\": \"APLICADA\", \"resumeVersionId\": %d}".formatted(resume));
	}

	@Test
	void ruleBasedRejectionWaitsForReviewAndTheConfirmationMovesTheCard() throws Exception {
		long emailId = GmailStub.receive(this.jdbc, this.googleApi, "g-1", "talentos@bancoexemplo.com.br",
				"Atualização sobre o processo", "Infelizmente seguimos com outra pessoa candidata.");

		assertThat(this.mailbox.classifyPending(10)).isEqualTo(1);
		await(() -> count("select count(*) from email_message where application_id is not null") == 1);

		this.mvc.perform(get("/api/emails").with(owner()))
			.andExpect(jsonPath("$", hasSize(1)))
			.andExpect(jsonPath("$[0].aiClassification").value("RECUSA"))
			.andExpect(jsonPath("$[0].aiConfidence").value(0.6))
			.andExpect(jsonPath("$[0].needsReview").value(true))
			.andExpect(jsonPath("$[0].applicationId").value(this.applicationId))
			.andExpect(jsonPath("$[0].applicationCompany").value("Banco Exemplo S.A."));
		assertThat(applicationStatus()).isEqualTo("APLICADA");

		this.mvc.perform(patch("/api/emails/{id}/classification", emailId).with(owner())
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"classification\": \"RECUSA\", \"applicationId\": %d}".formatted(this.applicationId)))
			.andExpect(status().isNoContent());

		await(() -> "RECUSADA".equals(applicationStatus()));
		this.mvc.perform(get("/api/emails").with(owner())).andExpect(jsonPath("$", hasSize(0)));
		this.mvc.perform(get("/api/emails/accuracy").with(owner()))
			.andExpect(jsonPath("$.reviewed").value(1))
			.andExpect(jsonPath("$.agreed").value(1))
			.andExpect(jsonPath("$.rate").value(1.0));
	}

	@Test
	void manualResponseFromWhatsAppMovesTheCardAndShowsInTheTimeline() throws Exception {
		this.mvc.perform(post("/api/applications/{id}/responses", this.applicationId).with(owner())
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"responseType": "CONVITE_ENTREVISTA", "channel": "WHATSAPP", "note": "Recrutadora chamou", "moveStatus": true}
					""")).andExpect(status().isNoContent());

		assertThat(applicationStatus()).isEqualTo("ENTREVISTA");
		long jobId = this.jdbc.sql("select job_id from application where id = :id").param("id", this.applicationId)
			.query(Long.class).single();
		this.mvc.perform(get("/api/jobs/{id}", jobId).with(owner()))
			.andExpect(jsonPath("$.timeline[1].responseType").value("CONVITE_ENTREVISTA"))
			.andExpect(jsonPath("$.timeline[1].responseChannel").value("WHATSAPP"))
			.andExpect(jsonPath("$.timeline[1].toStatus").value("ENTREVISTA"));
		this.mvc.perform(get("/api/applications/{id}/snapshot", this.applicationId).with(owner()))
			.andExpect(jsonPath("$.hasResponse").value(true));
	}

	private String applicationStatus() {
		return this.jdbc.sql("select status from application where id = :id").param("id", this.applicationId)
			.query(String.class).single();
	}

	private long count(String sql) {
		return this.jdbc.sql(sql).query(Long.class).single();
	}

}
