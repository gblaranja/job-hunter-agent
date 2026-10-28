package dev.laranja.jobhunter.tracking.internal;

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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Follow-up agendado ao aplicar, cancelado por um retorno do Gmail e lembrado uma vez por dia. */
class FollowUpIntegrationTests extends IntegrationTest {

	@Autowired
	private WireMockServer googleApi;

	@Autowired
	private SecretCipher cipher;

	@Autowired
	private Mailbox mailbox;

	@Autowired
	private FollowUpReminder reminder;

	private long applicationId;

	private long jobId;

	@BeforeEach
	void apply() throws Exception {
		this.googleApi.resetAll();
		GmailStub.connect(this.jdbc, this.cipher, this.googleApi);
		long resume = uploadResume(TestFiles.pdf("follow-up"), "cv.pdf", "Backend");
		this.jobId = createJob("Desenvolvedor Java", "Banco Exemplo S.A.", "Java e Spring");
		this.applicationId = startApplication(this.jobId,
				"{\"status\": \"APLICADA\", \"resumeVersionId\": %d}".formatted(resume));
	}

	@Test
	void applyingSchedulesAFollowUpAndAConfirmedEmailResponseCancelsIt() throws Exception {
		this.mvc.perform(get("/api/applications/board").with(owner()))
			.andExpect(jsonPath("$.columns[1].status").value("APLICADA"))
			.andExpect(jsonPath("$.columns[1].cards[0].nextActionKind").value("FOLLOW_UP"));
		assertThat(this.jdbc.sql("select next_action_at - applied_at = interval '7 days' from application")
			.query(Boolean.class).single()).isTrue();

		long emailId = GmailStub.receive(this.jdbc, this.googleApi, "g-1", "talentos@bancoexemplo.com.br",
				"Próximos passos", "Gostaríamos de agendar uma entrevista com você.");
		this.mailbox.classifyPending(10);
		awaitListeners();
		this.mvc.perform(patch("/api/emails/{id}/classification", emailId).with(owner())
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"classification\": \"CONVITE_ENTREVISTA\", \"applicationId\": %d}".formatted(this.applicationId)))
			.andExpect(status().isNoContent());

		await(() -> "ENTREVISTA".equals(column("status")));
		assertThat(column("next_action_at")).isNull();
		assertThat(column("next_action_kind")).isNull();
	}

	@Test
	void registeringFollowUpsReschedulesAndStopsAfterTheMaximum() throws Exception {
		this.mvc.perform(post("/api/applications/{id}/follow-ups", this.applicationId).with(owner()))
			.andExpect(status().isNoContent());

		this.mvc.perform(get("/api/jobs/{id}", this.jobId).with(owner()))
			.andExpect(jsonPath("$.application.followUpsSent").value(1))
			.andExpect(jsonPath("$.application.nextActionKind").value("FOLLOW_UP"))
			.andExpect(jsonPath("$.application.nextActionAuto").value(true));

		this.mvc.perform(post("/api/applications/{id}/follow-ups", this.applicationId).with(owner()))
			.andExpect(status().isNoContent());

		this.mvc.perform(get("/api/jobs/{id}", this.jobId).with(owner()))
			.andExpect(jsonPath("$.application.followUpsSent").value(2))
			.andExpect(jsonPath("$.application.nextActionAt").doesNotExist());
	}

	@Test
	void overdueFollowUpsBecomeOneNotificationPerDay() throws Exception {
		this.jdbc.sql("update application set next_action_at = now() - interval '1 day'").update();

		assertThat(this.reminder.remind()).isEqualTo(1);
		assertThat(this.reminder.remind()).isEqualTo(1);

		await(() -> this.jdbc.sql("select count(*) from notification where type = 'FOLLOW_UP'").query(Long.class).single() == 1);
		awaitListeners();
		assertThat(this.jdbc.sql("select count(*) from notification").query(Long.class).single()).isEqualTo(1);
		assertThat(this.jdbc.sql("select title from notification").query(String.class).single())
			.isEqualTo("1 follow-up para fazer hoje");
		assertThat(this.jdbc.sql("select body from notification").query(String.class).single())
			.isEqualTo("Follow-up: Banco Exemplo S.A.");
	}

	private Object column(String name) {
		return this.jdbc.sql("select " + name + " from application where id = :id")
			.param("id", this.applicationId)
			.query((rs, row) -> java.util.Optional.ofNullable(rs.getObject(1)))
			.single()
			.orElse(null);
	}

}
