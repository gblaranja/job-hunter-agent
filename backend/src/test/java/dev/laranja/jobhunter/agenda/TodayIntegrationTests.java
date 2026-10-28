package dev.laranja.jobhunter.agenda;

import com.github.tomakehurst.wiremock.WireMockServer;
import dev.laranja.jobhunter.IntegrationTest;
import dev.laranja.jobhunter.agenda.internal.DailySummary;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;

import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class TodayIntegrationTests extends IntegrationTest {

	@Autowired
	private DailySummary summary;

	@Autowired
	private WireMockServer google;

	@BeforeEach
	void reset() {
		this.google.resetAll();
	}

	@Test
	void todayListsWhatNeedsActionAndTheDailySummaryGoesOut() throws Exception {
		long good = job("Desenvolvedor Java", "Acme", 85, "ALTA", "[]", "now()");
		job("Vaga com eliminatório", "Beta", 90, "ALTA",
				"[{\"type\": \"IDIOMA\", \"status\": \"NAO_ATENDE\", \"reason\": \"Pede inglês fluente\"}]", "now()");
		job("Vaga suspeita", "Gama", 90, "SUSPEITA", "[]", "now()");
		job("Vaga antiga", "Delta", 90, "ALTA", "[]", "now() - interval '5 days'");
		job("Vaga fraca", "Épsilon", 60, "ALTA", "[]", "now()");
		long applied = job("Backend Kotlin", "Zeta", 75, "ALTA", "[]", "now() - interval '10 days'");
		this.jdbc.sql("""
				insert into application (job_id, status, applied_at, next_action_at, next_action_kind, follow_ups_sent)
				values (:jobId, 'APLICADA', now() - interval '8 days', now() - interval '1 day', 'FOLLOW_UP', 0)
				""").param("jobId", applied).update();
		long interview = job("SRE", "Eta", 70, "ALTA", "[]", "now() - interval '20 days'");
		this.jdbc.sql("""
				insert into application (job_id, status, applied_at, next_action_at)
				values (:jobId, 'ENTREVISTA', now() - interval '2 days', now() + interval '2 days')
				""").param("jobId", interview).update();
		this.jdbc.sql("""
				insert into email_message (gmail_id, sender, subject, received_at, kind, needs_review)
				values ('m1', 'rh@eta.com', 'Próximos passos', now(), 'OUTRO', true)
				""").update();

		this.mvc.perform(get("/api/today").with(owner()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.jobs[*].id", contains((int) good)))
			.andExpect(jsonPath("$.followUps[*].company", contains("Zeta")))
			.andExpect(jsonPath("$.followUps[0].nextActionKind").value("FOLLOW_UP"))
			.andExpect(jsonPath("$.emails[*].subject", contains("Próximos passos")))
			.andExpect(jsonPath("$.interviews[*].company", contains("Eta")))
			.andExpect(jsonPath("$.weeklyGoal.goal").value(12));

		assertThat(this.summary.send()).isTrue();
		awaitListeners();
		String body = this.jdbc.sql("select body from notification where type = 'RESUMO_DIARIO'")
			.query(String.class)
			.single();
		assertThat(body).contains("Desenvolvedor Java · Acme (85%)", "Follow-ups vencidos: Zeta", "1 e-mail para revisar");
	}

	@Test
	void applyCheckWarnsAboutTheLinkKnockoutsLegitimacyScoreAndTheCompany() throws Exception {
		this.google.stubFor(com.github.tomakehurst.wiremock.client.WireMock.get(urlPathEqualTo("/vaga-fechada")).willReturn(aResponse().withStatus(404)));
		long jobId = job("Dev", "Acme", 50, "BAIXA",
				"[{\"type\": \"ANOS_EXPERIENCIA\", \"status\": \"NAO_ATENDE\", \"reason\": \"Pede 8 anos\"}]", "now()");
		this.jdbc.sql("update job set url = :url, legitimacy_reasons = '[\"No ar há 75 dias\"]' where id = :id")
			.param("url", this.google.baseUrl() + "/vaga-fechada")
			.param("id", jobId)
			.update();
		for (int i = 0; i < 3; i++) {
			long other = job("Outra " + i, "Acme", 70, "ALTA", "[]", "now()");
			this.jdbc.sql("insert into application (job_id, status, applied_at) values (:id, 'APLICADA', now() - interval '30 days')")
				.param("id", other)
				.update();
		}

		this.mvc.perform(get("/api/jobs/{id}/apply-check", jobId).with(owner()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.linkStatus").value("REMOVIDO"))
			.andExpect(jsonPath("$.warnings[*].code", containsInAnyOrder("LINK_FORA_DO_AR", "ELIMINATORIO",
					"LEGITIMIDADE", "SCORE_BAIXO", "MUITAS_CANDIDATURAS")))
			.andExpect(jsonPath("$.warnings[?(@.code == 'ELIMINATORIO')].message", contains(containsString("Pede 8 anos"))));

		long clean = job("Dev Pleno", "Nova", 80, "ALTA", "[]", "now()");
		this.mvc.perform(get("/api/jobs/{id}/apply-check", clean).with(owner()))
			.andExpect(jsonPath("$.linkStatus").value("SEM_LINK"))
			.andExpect(jsonPath("$.warnings", hasSize(0)));
	}

	private long job(String title, String company, int score, String legitimacy, String knockouts, String firstSeen) {
		long companyId = this.jdbc.sql("""
				insert into company (name, normalized_name) values (:name, lower(:name))
				on conflict (normalized_name) do update set name = excluded.name returning id
				""").param("name", company).query(Long.class).single();
		long id = this.jdbc.sql("""
				insert into job (source, fingerprint, title, company, company_id, collected_at, first_seen_at, last_seen_at,
				                 legitimacy)
				values ('MANUAL', md5(random()::text), :title, :company, :companyId, now(), %s, now(), :legitimacy)
				returning id
				""".formatted(firstSeen))
			.param("title", title)
			.param("company", company)
			.param("companyId", companyId)
			.param("legitimacy", legitimacy)
			.query(Long.class)
			.single();
		this.jdbc.sql("""
				insert into job_analysis (job_id, status, model_version, match_score, knockouts, analyzed_at)
				values (:id, 'DETERMINISTICO', 'teste', :score, cast(:knockouts as jsonb), now())
				""").param("id", id).param("score", score).param("knockouts", knockouts).update();
		return id;
	}

}
