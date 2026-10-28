package dev.laranja.jobhunter.insights;

import dev.laranja.jobhunter.IntegrationTest;
import org.junit.jupiter.api.Test;

import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class FunnelBreakdownIntegrationTests extends IntegrationTest {

	@Test
	void opensTheFunnelBySourceChannelTimeToApplyAndTailoring() throws Exception {
		InsightFixtures.seed(this.jdbc);
		// Vagas 1 a 3 do Greenhouse e 4 a 6 dos alertas do LinkedIn; as candidaturas 1 e 2 vieram por indicação.
		this.jdbc.sql("update job set source = 'GREENHOUSE' where id in (1, 2, 3)").update();
		this.jdbc.sql("update job set source = 'LINKEDIN_ALERTA' where id in (4, 5, 6)").update();
		this.jdbc.sql("update application set channel = 'INDICACAO' where id in (1, 2)").update();
		this.jdbc.sql("update application set channel = 'EASY_APPLY' where id between 3 and 6").update();
		// Aplicou na vaga 1 em 10 h, na vaga 2 em 1 dia, nas demais depois de uma semana.
		this.jdbc.sql("update application set applied_at = now() - interval '4 days' where id = 2").update();
		this.jdbc.sql("""
				update job j set first_seen_at = a.applied_at - case j.id when 1 then interval '10 hours'
				                                                         when 2 then interval '1 day'
				                                                         else interval '10 days' end
				from application a where a.job_id = j.id
				""").update();
		// A candidatura 2 teve o primeiro retorno 2 dias depois de aplicar; a 6 usou um currículo adaptado.
		this.jdbc.sql("""
				insert into application_event (application_id, response_type, response_channel, origin, created_at)
				values (2, 'CONVITE_ENTREVISTA', 'EMAIL', 'EMAIL', now() - interval '2 days')
				""").update();
		this.jdbc.sql("""
				insert into tailored_resume (job_id, status, resume_version_id, created_at, updated_at)
				values (6, 'PRONTO', 2, now(), now())
				""").update();
		// A candidatura 1 recebeu uma proposta e aceitou.
		this.jdbc.sql("""
				insert into offer (application_id, regime, base_salary, decision, created_at, updated_at)
				values (1, 'CLT', 12000, 'ACEITA', now(), now())
				""").update();

		this.mvc.perform(get("/api/insights/funnel").with(owner()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.applied").value(6))
			.andExpect(jsonPath("$.interview").value(2))
			.andExpect(jsonPath("$.offer").value(1))
			.andExpect(jsonPath("$.accepted").value(1))
			.andExpect(jsonPath("$.bySource[?(@.group == 'GREENHOUSE')].applied", contains(3)))
			.andExpect(jsonPath("$.bySource[?(@.group == 'GREENHOUSE')].interview", contains(2)))
			.andExpect(jsonPath("$.bySource[?(@.group == 'LINKEDIN_ALERTA')].interview", contains(0)))
			.andExpect(jsonPath("$.byChannel[?(@.group == 'INDICACAO')].applied", contains(2)))
			.andExpect(jsonPath("$.byChannel[?(@.group == 'INDICACAO')].offer", contains(1)))
			.andExpect(jsonPath("$.byChannel[?(@.group == 'EASY_APPLY')].applied", contains(4)))
			.andExpect(jsonPath("$.byTimeToApply[?(@.group == 'ATE_1_DIA')].interview", contains(1)))
			.andExpect(jsonPath("$.byTimeToApply[?(@.group == '1_A_3_DIAS')].applied", contains(1)))
			.andExpect(jsonPath("$.byTimeToApply[?(@.group == 'MAIS_DE_7_DIAS')].applied", contains(4)))
			.andExpect(jsonPath("$.byTailoring[?(@.group == 'ADAPTADO')].applied", contains(1)))
			.andExpect(jsonPath("$.byTailoring[?(@.group == 'PADRAO')].applied", contains(5)))
			.andExpect(jsonPath("$.medianDaysToFirstResponse").value(2.0))
			// As 6 vagas com candidatura (score 62) contra 6 com "não gostei" (duas com 62 e quatro com 85).
			.andExpect(jsonPath("$.rankingQuality.evaluated").value(12))
			.andExpect(jsonPath("$.rankingQuality.positives").value(6))
			.andExpect(jsonPath("$.rankingQuality.topCount").value(12))
			.andExpect(jsonPath("$.rankingQuality.avgScorePositive").value(62))
			.andExpect(jsonPath("$.rankingQuality.avgScoreNegative").value(77));
	}

	@Test
	void emptyFunnelWithoutApplications() throws Exception {
		this.mvc.perform(get("/api/insights/funnel").with(owner()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.applied").value(0))
			.andExpect(jsonPath("$.bySource").isEmpty())
			.andExpect(jsonPath("$.medianDaysToFirstResponse").doesNotExist())
			.andExpect(jsonPath("$.rankingQuality.evaluated").value(0));
	}

}
