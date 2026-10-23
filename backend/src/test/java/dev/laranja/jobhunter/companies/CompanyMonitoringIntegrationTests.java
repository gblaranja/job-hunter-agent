package dev.laranja.jobhunter.companies;

import com.jayway.jsonpath.JsonPath;
import dev.laranja.jobhunter.IntegrationTest;
import org.junit.jupiter.api.Test;

import org.springframework.http.MediaType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CompanyMonitoringIntegrationTests extends IntegrationTest {

	@Test
	void weeklyMetricsRepostsAndAlertsForATargetCompany() throws Exception {
		long first = postJob("Desenvolvedor Backend Java", "Remoto");
		long companyId = this.jdbc.sql("select company_id from job where id = :id").param("id", first).query(Long.class).single();
		this.mvc.perform(patch("/api/companies/{id}", companyId).with(owner())
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"watchStatus\": \"ALVO\"}")).andExpect(status().isOk());
		// Vaga antiga, aberta há 100 dias e fechada há 90: a empresa ficou mais de 60 dias sem abrir vagas.
		this.jdbc.sql("""
				insert into job (source, fingerprint, title, company, company_id, first_seen_at, last_seen_at, closed_at)
				values ('MANUAL', 'fp-antiga', 'Analista de Dados', 'Alfa', :companyId, now() - interval '100 days',
				        now() - interval '90 days', now() - interval '90 days')
				""").param("companyId", companyId).update();
		// A primeira vaga sai do ar e volta com o mesmo título: é um repost.
		this.jdbc.sql("update job set closed_at = now() where id = :id").param("id", first).update();
		long repost = postJob("Desenvolvedor Backend Java", "São Paulo");
		assertThat(this.jdbc.sql("select repost_of_job_id from job where id = :id").param("id", repost).query(Long.class).single())
			.isEqualTo(first);

		this.mvc.perform(post("/api/companies/refresh-metrics").with(owner())).andExpect(status().isNoContent());

		String metrics = this.mvc.perform(get("/api/companies/{id}/metrics", companyId).with(owner()))
			.andExpect(status().isOk())
			.andReturn()
			.getResponse()
			.getContentAsString();
		Integer weeks = JsonPath.read(metrics, "$.length()");
		assertThat(weeks).isBetween(14, 16);
		assertThat((Integer) JsonPath.read(metrics, "$[0].jobsOpened")).isEqualTo(1);
		assertThat((Integer) JsonPath.read(metrics, "$[-1].jobsOpened")).isEqualTo(2);
		assertThat((Integer) JsonPath.read(metrics, "$[-1].reposts")).isEqualTo(1);
		// Ativas em algum momento da semana: a primeira fechou nesta semana e ainda conta.
		assertThat((Integer) JsonPath.read(metrics, "$[-1].jobsActive")).isEqualTo(2);
		assertThat((Integer) JsonPath.read(metrics, "$[-1].jobsClosed")).isEqualTo(1);

		this.mvc.perform(put("/api/companies/{id}/headcount", companyId).with(owner())
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"headcount\": 1200}")).andExpect(status().isNoContent());
		this.mvc.perform(get("/api/companies/{id}/metrics", companyId).with(owner()))
			.andExpect(jsonPath("$[-1].headcount").value(1200));

		this.mvc.perform(get("/api/companies/{id}/overview", companyId).with(owner()))
			.andExpect(jsonPath("$.jobs[0].repostOfJobId").value((int) first))
			.andExpect(jsonPath("$.byWorkModel.REMOTO").value(2));
		this.mvc.perform(get("/api/companies/{id}", companyId).with(owner()))
			.andExpect(jsonPath("$.reposts").value(1))
			.andExpect(jsonPath("$.avgDaysOpen", notNullValue()));

		// Os alertas viram notificações pelo listener assíncrono; o recálculo não repete os avisos.
		await(() -> notifications() >= 3);
		this.mvc.perform(post("/api/companies/refresh-metrics").with(owner())).andExpect(status().isNoContent());
		Thread.sleep(500);
		assertThat(notifications()).isEqualTo(3);
		String list = this.mvc.perform(get("/api/notifications").with(owner()))
			.andExpect(jsonPath("$[*].type").value(hasItem("EMPRESA_VOLTOU")))
			.andExpect(jsonPath("$[*].type").value(hasItem("EMPRESA_ALVO_VAGA")))
			.andReturn()
			.getResponse()
			.getContentAsString();
		Integer id = JsonPath.read(list, "$[0].id");
		this.mvc.perform(patch("/api/notifications/{id}/read", id).with(owner())).andExpect(status().is2xxSuccessful());
		assertThat(this.jdbc.sql("select count(*) from notification where read_at is null").query(Long.class).single())
			.isEqualTo(2);
	}

	private long notifications() {
		return this.jdbc.sql("select count(*) from notification").query(Long.class).single();
	}

	private long postJob(String title, String location) throws Exception {
		String response = this.mvc.perform(post("/api/jobs").with(owner()).contentType(MediaType.APPLICATION_JSON).content("""
				{"title": "%s", "company": "Alfa", "location": "%s", "workModel": "REMOTO",
				 "url": "https://example.com/vagas/%d", "description": "Java"}
				""".formatted(title, location, System.nanoTime())))
			.andExpect(status().isCreated())
			.andReturn()
			.getResponse()
			.getContentAsString();
		return ((Number) JsonPath.read(response, "$.id")).longValue();
	}

}
