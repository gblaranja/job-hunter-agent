package dev.laranja.jobhunter.sourcing;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Base64;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import dev.laranja.jobhunter.IntegrationTest;
import dev.laranja.jobhunter.shared.security.SecretCipher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CollectJobsIntegrationTests extends IntegrationTest {

	private static final String API = "/gmail/v1/users/me";

	@Autowired
	private WireMockServer google;

	@Autowired
	private SecretCipher cipher;

	@BeforeEach
	void connectGmail() {
		this.google.resetAll();
		// Credencial já conectada, com o access token vencido: a primeira chamada renova pelo refresh token.
		this.jdbc.sql("""
				insert into oauth_credential (provider, account_email, access_token_enc, access_token_expires_at,
				                              refresh_token_enc, scopes)
				values ('GOOGLE', 'eu@example.com', :access, :expired, :refresh, 'gmail.readonly')
				""")
			.param("access", this.cipher.encrypt("old-access-token"))
			.param("expired", Timestamp.from(Instant.now().minusSeconds(3600)))
			.param("refresh", this.cipher.encrypt("refresh-token"))
			.update();
		this.google.stubFor(WireMock.post("/token").willReturn(okJson("""
				{"access_token": "fresh-access-token", "expires_in": 3599, "scope": "gmail.readonly"}
				""")));
		this.google.stubFor(WireMock.get(API + "/labels").willReturn(okJson("""
				{"labels": [{"id": "INBOX", "name": "INBOX"}, {"id": "Label_7", "name": "jobs"}]}
				""")));
		this.google.stubFor(WireMock.get(API + "/profile").willReturn(okJson("""
				{"emailAddress": "eu@example.com", "historyId": "1000"}
				""")));
	}

	@Test
	void collectsAlertsFromGmailFiltersByProfileAndIsIdempotent() throws Exception {
		this.google.stubFor(WireMock.get(urlPathEqualTo(API + "/messages")).withQueryParam("labelIds", equalTo("Label_7"))
			.willReturn(okJson("""
					{"messages": [{"id": "m-linkedin"}, {"id": "m-recruiter"}, {"id": "m-gupy"}]}
					""")));
		stubMessage("m-linkedin", "LinkedIn Job Alerts <jobalerts-noreply@linkedin.com>", "Novas vagas: Java Spring Boot",
				"linkedin-alerta.html");
		stubMessage("m-recruiter", "Ana <ana@empresa.example>", "Convite para entrevista", null);
		stubMessage("m-gupy", "Gupy <alertas@email.gupy.io>", "2 novas vagas para você", "gupy-alerta.html");

		this.mvc.perform(post("/api/admin/collect").with(owner()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("CONCLUIDA"))
			.andExpect(jsonPath("$.trigger").value("MANUAL"))
			// 3 vagas do LinkedIn (uma é estágio, excluída pelo perfil padrão) e 2 da Gupy
			.andExpect(jsonPath("$.stats.GMAIL_ALERTAS.created").value(4))
			.andExpect(jsonPath("$.stats.GMAIL_ALERTAS.excluded").value(1))
			.andExpect(jsonPath("$.stats.GMAIL_ALERTAS.errors").value(0));

		assertThat(count("select count(*) from email_message")).isEqualTo(3);
		assertThat(count("select count(*) from email_message where kind = 'ALERTA' and parsed_at is not null")).isEqualTo(2);
		assertThat(count("select count(*) from email_message where kind = 'OUTRO'")).isEqualTo(1);
		// "Banco Exemplo" (LinkedIn) e "Banco Exemplo S.A." (Gupy) são a mesma empresa
		assertThat(count("select count(*) from company where normalized_name = 'banco exemplo'")).isEqualTo(1);
		assertThat(count("select count(distinct company_id) from job where company like 'Banco Exemplo%'")).isEqualTo(1);
		this.google.verify(postRequestedFor(urlPathEqualTo("/token")));
		this.google.verify(getRequestedFor(urlPathEqualTo(API + "/labels")).withHeader("Authorization",
				equalTo("Bearer fresh-access-token")));

		this.mvc.perform(get("/api/jobs").param("inbox", "true").with(owner()))
			.andExpect(jsonPath("$.total").value(4))
			.andExpect(jsonPath("$.items[?(@.title == 'Desenvolvedor Java Sênior')].url")
				.value("https://www.linkedin.com/jobs/view/4012345678/"));

		// Segunda coleta: o histórico traz um alerta novo que repete uma vaga já conhecida.
		this.google.stubFor(WireMock.get(urlPathEqualTo(API + "/history")).withQueryParam("startHistoryId", equalTo("1000"))
			.willReturn(okJson("""
					{"history": [{"messagesAdded": [{"message": {"id": "m-linkedin-2"}}]}], "historyId": "1010"}
					""")));
		stubMessage("m-linkedin-2", "jobalerts-noreply@linkedin.com", "Novas vagas", "linkedin-alerta.html");

		this.mvc.perform(post("/api/admin/collect").with(owner()))
			.andExpect(jsonPath("$.stats.GMAIL_ALERTAS.created").value(0))
			.andExpect(jsonPath("$.stats.GMAIL_ALERTAS.duplicate").value(2))
			.andExpect(jsonPath("$.stats.GMAIL_ALERTAS.excluded").value(1));

		assertThat(count("select count(*) from job")).isEqualTo(4);
		assertThat(count("select last_history_id from gmail_sync_state where label_id = 'Label_7'")).isEqualTo(1010);
		this.mvc.perform(get("/api/admin/collection-runs").with(owner())).andExpect(jsonPath("$", hasSize(2)));
	}

	@Test
	void expiredHistoryPointFallsBackToAFullListing() throws Exception {
		this.jdbc.sql("insert into gmail_sync_state (label_id, last_history_id, last_full_sync_at) values ('Label_7', 5, now())")
			.update();
		this.google.stubFor(WireMock.get(urlPathEqualTo(API + "/history")).willReturn(aResponse().withStatus(404)));
		this.google.stubFor(WireMock.get(urlPathEqualTo(API + "/messages")).willReturn(okJson("""
				{"messages": [{"id": "m-gupy"}]}
				""")));
		stubMessage("m-gupy", "alertas@email.gupy.io", "Novas vagas", "gupy-alerta.html");

		this.mvc.perform(post("/api/admin/collect").with(owner()))
			.andExpect(jsonPath("$.status").value("CONCLUIDA"))
			.andExpect(jsonPath("$.stats.GMAIL_ALERTAS.created").value(2));

		assertThat(count("select last_history_id from gmail_sync_state")).isEqualTo(1000);
	}

	@Test
	void revokedAuthorizationFailsTheRunWithAClearMessage() throws Exception {
		this.google.stubFor(WireMock.post("/token").willReturn(aResponse().withStatus(400).withBody("{\"error\": \"invalid_grant\"}")));

		this.mvc.perform(post("/api/admin/collect").with(owner()))
			.andExpect(jsonPath("$.status").value("FALHOU"))
			.andExpect(jsonPath("$.error").value(org.hamcrest.Matchers.containsString("Conecte de novo")));
	}

	private void stubMessage(String id, String from, String subject, String fixture) throws IOException {
		this.google.stubFor(WireMock.get(urlPathEqualTo(API + "/messages/" + id)).withQueryParam("format", equalTo("metadata"))
			.willReturn(okJson("""
					{"id": "%s", "threadId": "t-%s", "snippet": "...", "internalDate": "1792700000000",
					 "payload": {"headers": [{"name": "From", "value": "%s"}, {"name": "Subject", "value": "%s"}]}}
					""".formatted(id, id, from, subject))));
		if (fixture != null) {
			String html = new ClassPathResource("fixtures/alerts/" + fixture).getContentAsString(StandardCharsets.UTF_8);
			String data = Base64.getUrlEncoder().withoutPadding().encodeToString(html.getBytes(StandardCharsets.UTF_8));
			this.google.stubFor(WireMock.get(urlPathEqualTo(API + "/messages/" + id)).withQueryParam("format", equalTo("full"))
				.willReturn(okJson("""
						{"id": "%s", "payload": {"mimeType": "multipart/alternative", "parts": [
						  {"mimeType": "text/plain", "body": {"data": "", "size": 0}},
						  {"mimeType": "text/html", "body": {"data": "%s", "size": %d}}]}}
						""".formatted(id, data, html.length()))));
		}
	}

	private long count(String sql) {
		return this.jdbc.sql(sql).query(Long.class).single();
	}

}
