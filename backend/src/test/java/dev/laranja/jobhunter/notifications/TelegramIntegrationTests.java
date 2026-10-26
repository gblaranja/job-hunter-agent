package dev.laranja.jobhunter.notifications;

import java.util.List;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import dev.laranja.jobhunter.IntegrationTest;
import dev.laranja.jobhunter.analysis.JobAnalyzed;
import dev.laranja.jobhunter.shared.NotificationRequested;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.support.TransactionTemplate;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@TestPropertySource(properties = { "jobhunter.notifications.telegram.bot-token=123456:SEGREDO-do-bot",
		"jobhunter.notifications.telegram.chat-id=4242", "jobhunter.notifications.public-url=https://jobs.exemplo.dev/" })
class TelegramIntegrationTests extends IntegrationTest {

	private static final String SEND = "/bot123456:SEGREDO-do-bot/sendMessage";

	@Autowired
	private WireMockServer googleApi;

	@Autowired
	private Notifications notifications;

	@Autowired
	private ApplicationEventPublisher events;

	@Autowired
	private TransactionTemplate transactions;

	@BeforeEach
	void telegramAnswers() {
		this.googleApi.resetAll();
		this.googleApi.stubFor(post(urlPathEqualTo(SEND)).willReturn(okJson("{\"ok\": true}")));
	}

	@Test
	void sendsConfiguredTypesOnceAfterCommitWithAbsoluteLink() throws Exception {
		this.notifications.notify("FOLLOW_UP", "Follow-up da Acme <hoje>", "2 candidaturas esperando", "/board",
				"follow-up-1");
		// Repetida pela dedup_key e de um tipo que fica só no sino: nenhuma vai ao Telegram.
		this.notifications.notify("FOLLOW_UP", "Follow-up da Acme <hoje>", "2 candidaturas esperando", "/board",
				"follow-up-1");
		this.notifications.notify("RELATORIO_SEMANAL", "Relatório semanal pronto", null, "/insights", null);
		awaitListeners();

		List<LoggedRequest> sent = this.googleApi.findAll(postRequestedFor(urlPathEqualTo(SEND)));
		assertThat(sent).hasSize(1);
		String body = sent.getFirst().getBodyAsString();
		assertThat(body).contains("\"chat_id\":\"4242\"", "\"parse_mode\":\"HTML\"",
				"<b>Follow-up da Acme &lt;hoje&gt;</b>", "2 candidaturas esperando",
				"https://jobs.exemplo.dev/board");
	}

	@Test
	void requestedNotificationsFromOtherModulesReachTheBellAndTelegram() throws Exception {
		this.transactions.executeWithoutResult(tx -> this.events.publishEvent(new NotificationRequested(
				"PREPARACAO_PRONTA", "Preparação pronta: Acme", "Entrevista técnica", "/jobs/7", "preparacao-1")));
		await(() -> this.googleApi.findAll(postRequestedFor(urlPathEqualTo(SEND))).size() == 1);

		assertThat(this.jdbc.sql("select title from notification where type = 'PREPARACAO_PRONTA'")
			.query(String.class)
			.list()).containsExactly("Preparação pronta: Acme");
	}

	@Test
	void targetCompanyJobAboveSixtyGoesToTelegram() throws Exception {
		long jobId = createJob("Desenvolvedor Java", "Banco Alvo", "Java e Spring Boot");
		long otherId = createJob("Desenvolvedor Go", "Outra Empresa", "Go e Kubernetes");
		awaitListeners();
		this.jdbc.sql("update company set watch_status = 'ALVO' where name = 'Banco Alvo'").update();

		this.transactions.executeWithoutResult(tx -> {
			this.events.publishEvent(new JobAnalyzed(jobId, 64));
			this.events.publishEvent(new JobAnalyzed(otherId, 64));
		});
		await(() -> this.googleApi.findAll(postRequestedFor(urlPathEqualTo(SEND))).size() == 1);
		awaitListeners();

		assertThat(this.jdbc.sql("select type from notification order by id").query(String.class).list())
			.containsExactly("VAGA_ALVO");
		assertThat(this.googleApi.findAll(postRequestedFor(urlPathEqualTo(SEND))).getFirst().getBodyAsString())
			.contains("Desenvolvedor Java", "64%", "https://jobs.exemplo.dev/jobs/" + jobId);
	}

	@Test
	void settingsShowStatusAndSendTestWithoutLeakingTheToken() throws Exception {
		this.mvc.perform(get("/api/notifications/telegram").with(owner()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.configured").value(true))
			.andExpect(jsonPath("$.types[0]").value("VAGA_ALVO"))
			.andExpect(content -> assertThat(content.getResponse().getContentAsString()).doesNotContain("SEGREDO"));

		this.mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
			.post("/api/notifications/telegram/test")
			.with(owner())).andExpect(status().isNoContent());
		assertThat(this.googleApi.findAll(postRequestedFor(urlPathEqualTo(SEND))).getFirst().getBodyAsString())
			.contains("Teste do Job Hunter Agent");

		this.googleApi.stubFor(post(urlPathEqualTo(SEND)).willReturn(aResponse().withStatus(401)
			.withHeader("Content-Type", "application/json")
			.withBody("{\"ok\": false, \"description\": \"Unauthorized\"}")));
		this.mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
			.post("/api/notifications/telegram/test")
			.with(owner()))
			.andExpect(status().isUnprocessableContent())
			.andExpect(jsonPath("$.detail", containsString("Falha ao enviar ao Telegram")))
			.andExpect(jsonPath("$.detail", not(containsString("SEGREDO"))));
	}

}
