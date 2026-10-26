package dev.laranja.jobhunter.analysis;

import dev.laranja.jobhunter.IntegrationTest;
import dev.laranja.jobhunter.TestFiles;
import org.junit.jupiter.api.Test;

import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Eliminatórios sem LLM: a vaga que pede inglês fluente e 8 anos limita o score de quem não tem. */
class KnockoutIntegrationTests extends IntegrationTest {

	@Test
	void failedKnockoutsCapTheScoreAndFollowTheProfile() throws Exception {
		saveProfile(6, "AVANCADO");
		uploadResume(TestFiles.pdfWithText("Desenvolvedor Java Senior Spring Boot Kafka AWS"), "cv.pdf", "Backend");
		await(() -> count("select count(*) from resume_version where jsonb_array_length(skills) > 0") == 1);

		long jobId = createJob("Desenvolvedor Java Sênior", "Banco",
				"Requisitos: Java, Spring Boot, Kafka e AWS. Pelo menos 8 anos de experiência. Inglês fluente.");
		await(() -> count("select count(*) from job_analysis where job_id = " + jobId) == 1);

		this.mvc.perform(get("/api/jobs/{id}", jobId).with(owner()))
			.andExpect(jsonPath("$.analysis.matchScore").value(40))
			.andExpect(jsonPath("$.analysis.knockouts.length()").value(2))
			.andExpect(jsonPath("$.analysis.knockouts[0].status").value("NAO_ATENDE"))
			.andExpect(jsonPath("$.analysis.knockouts[1].reason").value("Seu inglês: avançado; a vaga pede fluente."));
		this.mvc.perform(get("/api/jobs").param("inbox", "true").with(owner()))
			.andExpect(jsonPath("$.items[0].failedKnockouts").value(2));

		// sem eliminatório falho: obrigatórias 4/5 (Inglês é skill do dicionário) 56, desejáveis 12, senioridade 5, remoto 5
		saveProfile(10, "FLUENTE");
		awaitListeners();
		this.mvc.perform(get("/api/jobs/{id}", jobId).with(owner())).andExpect(jsonPath("$.analysis.matchScore").value(78));
		this.mvc.perform(get("/api/jobs").param("inbox", "true").with(owner()))
			.andExpect(jsonPath("$.items[0].failedKnockouts").value(0));
	}

	private void saveProfile(int years, String english) throws Exception {
		this.mvc.perform(put("/api/candidate-profile").with(owner()).contentType(MediaType.APPLICATION_JSON)
			.content("{\"yearsOfExperience\": %d, \"englishLevel\": \"%s\"}".formatted(years, english)))
			.andExpect(status().isOk());
	}

	private long count(String sql) {
		Long value = this.jdbc.sql(sql).query(Long.class).single();
		return (value == null) ? 0 : value;
	}

}
