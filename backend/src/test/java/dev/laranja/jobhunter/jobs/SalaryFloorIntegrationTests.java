package dev.laranja.jobhunter.jobs;

import dev.laranja.jobhunter.IntegrationTest;
import org.junit.jupiter.api.Test;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SalaryFloorIntegrationTests extends IntegrationTest {

	@Test
	void inboxShowsTheAnnualEquivalentAndHidesJobsBelowYourFloor() throws Exception {
		long low = createJob("Desenvolvedor Java", "Banco Alfa", "Java e Spring Boot. Remoto.");
		long pj = createJob("Desenvolvedor Kotlin", "Fintech Beta", "Kotlin e Spring Boot. Remoto.");
		long none = createJob("Desenvolvedor Go", "Startup Gama", "Go e Kafka. Remoto.");
		awaitListeners();
		salary(low, "{\"min\": 4000, \"max\": 5000, \"currency\": \"BRL\", \"period\": \"MENSAL\", \"regime\": \"CLT\"}");
		salary(pj, "{\"min\": null, \"max\": 30000, \"currency\": \"BRL\", \"period\": \"MENSAL\", \"regime\": \"PJ\"}");
		// Pretensão: R$ 15.000 CLT (piso anual 15.000 × 13,33 × 1,08 + 12.000 = 227.946) e R$ 20.000 PJ (199.200).
		this.jdbc.sql("""
				insert into candidate_profile (id, desired_salary_clt, desired_salary_pj, updated_at)
				values (1, 15000, 20000, now())
				on conflict (id) do update set desired_salary_clt = 15000, desired_salary_pj = 20000
				""").update();

		// CLT 5.000: 5.000 × 13,33 × 1,08 + 12.000 = 83.982; PJ 30.000: (30.000 × 0,85 − 400) × 12 = 301.200.
		this.mvc.perform(get("/api/jobs").param("inbox", "true").with(owner()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.total").value(3))
			.andExpect(jsonPath("$.items[?(@.id == " + low + ")].salaryEquivalent").value(83982.0))
			.andExpect(jsonPath("$.items[?(@.id == " + low + ")].belowSalaryFloor").value(true))
			.andExpect(jsonPath("$.items[?(@.id == " + pj + ")].salaryEquivalent").value(301200.0))
			.andExpect(jsonPath("$.items[?(@.id == " + pj + ")].belowSalaryFloor").value(false))
			.andExpect(jsonPath("$.items[?(@.id == " + none + ")].salaryEquivalent", org.hamcrest.Matchers.contains((Object) null)));

		this.mvc.perform(get("/api/jobs").param("inbox", "true").param("salaryFloor", "true").with(owner()))
			.andExpect(jsonPath("$.total").value(2))
			.andExpect(jsonPath("$.items[*].id", containsInAnyOrder((int) pj, (int) none)));
	}

	private void salary(long jobId, String json) {
		this.jdbc.sql("update job_analysis set salary = cast(:salary as jsonb) where job_id = :jobId")
			.param("salary", json)
			.param("jobId", jobId)
			.update();
	}

}
