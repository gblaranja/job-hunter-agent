package dev.laranja.jobhunter.sourcing;

import java.util.List;

import dev.laranja.jobhunter.IntegrationTest;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SourceHealthIntegrationTests extends IntegrationTest {

	@Autowired
	private SourceHealth health;

	@Test
	void reportsLastNewJobErrorsAndUnrecognizedAlerts() throws Exception {
		// O Greenhouse está em uso (empresa monitorada com slug) e falhou nas três últimas coletas.
		this.jdbc.sql("""
				insert into company (name, normalized_name, watch_status, ats_type, ats_slug)
				values ('Banco Alfa', 'banco alfa', 'MONITORADA', 'GREENHOUSE', 'bancoalfa')
				""").update();
		run("10 days", """
				{"GREENHOUSE": {"created": 4, "duplicate": 0, "excluded": 0, "errors": 0},
				 "GMAIL_ALERTAS": {"created": 2, "duplicate": 1, "excluded": 0, "errors": 0}}""");
		for (String age : List.of("3 days", "2 days", "1 day")) {
			run(age, """
					{"GREENHOUSE": {"created": 0, "duplicate": 0, "excluded": 0, "errors": 1},
					 "GMAIL_ALERTAS": {"created": 1, "duplicate": 0, "excluded": 0, "errors": 0}}""");
		}
		// Um alerta da Gupy lido hoje sem nenhuma vaga reconhecida e um do LinkedIn que gerou vagas.
		alert("g-1", "alertas@gupy.io", 0);
		alert("g-2", "jobalerts-noreply@linkedin.com", 3);

		this.mvc.perform(get("/api/admin/sources/health").with(owner()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.sources[?(@.source == 'GREENHOUSE')].available", contains(true)))
			.andExpect(jsonPath("$.sources[?(@.source == 'GREENHOUSE')].failingRunsInRow", contains(3)))
			.andExpect(jsonPath("$.sources[?(@.source == 'GREENHOUSE')].newJobsLast7Days", contains(0)))
			.andExpect(jsonPath("$.sources[?(@.source == 'GREENHOUSE')].lastNewJobAt").isNotEmpty())
			.andExpect(jsonPath("$.sources[?(@.source == 'GMAIL_ALERTAS')].newJobsLast7Days", contains(3)))
			.andExpect(jsonPath("$.sources[?(@.source == 'GMAIL_ALERTAS')].failingRunsInRow", contains(0)))
			.andExpect(jsonPath("$.unrecognizedAlerts[*].sender", contains("alertas@gupy.io")));

		this.health.check();
		this.health.check();

		List<String> titles = this.jdbc.sql("select title from notification where type = 'FONTE_COM_PROBLEMA' order by id")
			.query(String.class)
			.list();
		// Um aviso por problema por dia, mesmo rodando a verificação duas vezes.
		assertThat(titles).containsExactly("1 alerta de vaga não virou vaga",
				"A fonte GREENHOUSE falhou nas últimas 3 coletas");
	}

	private void run(String age, String stats) {
		this.jdbc.sql("""
				insert into collection_run (trigger, status, started_at, finished_at, stats)
				values ('AGENDADA', 'CONCLUIDA', now() - cast(:age as interval), now() - cast(:age as interval),
				        cast(:stats as jsonb))
				""").param("age", age).param("stats", stats).update();
	}

	private void alert(String gmailId, String sender, int jobsFound) {
		this.jdbc.sql("""
				insert into email_message (gmail_id, sender, subject, received_at, kind, parsed_at, jobs_found)
				values (:gmailId, :sender, 'Novas vagas para você', now(), 'ALERTA', now(), :jobs)
				""").param("gmailId", gmailId).param("sender", sender).param("jobs", jobsFound).update();
	}

}
