package dev.laranja.jobhunter.network;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import dev.laranja.jobhunter.AnthropicStubConfiguration;
import dev.laranja.jobhunter.GmailStub;
import dev.laranja.jobhunter.IntegrationTest;
import dev.laranja.jobhunter.inbox.Mailbox;
import dev.laranja.jobhunter.shared.security.SecretCipher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static dev.laranja.jobhunter.AnthropicStubConfiguration.messageWith;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

/** Um e-mail de headhunter vira uma vaga na caixa com o contato ligado. */
@Import(AnthropicStubConfiguration.class)
class RecruiterOutreachIntegrationTests extends IntegrationTest {

	@Autowired
	private WireMockServer googleApi;

	@Autowired
	private WireMockServer anthropicApi;

	@Autowired
	private SecretCipher cipher;

	@Autowired
	private Mailbox mailbox;

	@BeforeEach
	void connect() {
		this.googleApi.resetAll();
		this.anthropicApi.resetAll();
		this.anthropicApi.stubFor(WireMock.post("/v1/messages").atPriority(10).willReturn(aResponse().withStatus(500)));
		this.anthropicApi.stubFor(WireMock.post("/v1/messages")
			.atPriority(1)
			.withRequestBody(containing("Vi seu perfil"))
			.willReturn(okJson(messageWith("""
					{"classification": "CONTATO_RECRUTADOR", "company": "Fintech Exemplo", "jobTitle": "Engenheiro Backend Sênior",
					 "confidence": 0.9, "rationale": "Headhunter oferecendo uma vaga.", "recruiterName": "Marina Costa",
					 "agency": "Talentos Agência"}
					"""))));
		GmailStub.connect(this.jdbc, this.cipher, this.googleApi);
	}

	@Test
	void aHeadhunterEmailBecomesAnInboxJobWithTheContact() throws Exception {
		long emailId = GmailStub.receive(this.jdbc, this.googleApi, "g-rec", "marina@talentos.com.br",
				"Oportunidade Backend", "Vi seu perfil e tenho uma vaga de Engenheiro Backend Sênior na Fintech Exemplo.");

		this.mailbox.classifyPending(10);
		await(() -> count("select count(*) from contact_interaction") == 1);
		awaitListeners();

		this.mvc.perform(get("/api/jobs").param("inbox", "true").with(owner()))
			.andExpect(jsonPath("$.items[0].origin").value("RECRUTADOR"))
			.andExpect(jsonPath("$.items[0].company").value("Fintech Exemplo"))
			.andExpect(jsonPath("$.items[0].title").value("Engenheiro Backend Sênior"));
		this.mvc.perform(get("/api/contacts").with(owner()))
			.andExpect(jsonPath("$.items[0].name").value("Marina Costa"))
			.andExpect(jsonPath("$.items[0].role").value("HEADHUNTER"))
			.andExpect(jsonPath("$.items[0].companyName").value("Talentos Agência"))
			.andExpect(jsonPath("$.items[0].source").value("EMAIL"));
		assertThat(this.jdbc.sql("select needs_review from email_message where id = :id").param("id", emailId)
			.query(Boolean.class).single()).isFalse();

		// O mesmo e-mail processado de novo não duplica vaga nem conversa.
		this.jdbc.sql("update email_message set classified_at = null").update();
		this.mailbox.classifyPending(10);
		awaitListeners();
		assertThat(count("select count(*) from contact_interaction")).isEqualTo(1);
		assertThat(count("select count(*) from job")).isEqualTo(1);
		assertThat(count("select count(*) from contact")).isEqualTo(1);
	}

	private long count(String sql) {
		return this.jdbc.sql(sql).query(Long.class).single();
	}

}
