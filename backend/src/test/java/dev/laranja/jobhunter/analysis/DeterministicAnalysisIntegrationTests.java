package dev.laranja.jobhunter.analysis;

import dev.laranja.jobhunter.IntegrationTest;
import dev.laranja.jobhunter.TestFiles;
import org.junit.jupiter.api.Test;

import org.springframework.http.MediaType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Sem chave da API, a análise roda só com o dicionário e as regras, e o score continua funcionando. */
class DeterministicAnalysisIntegrationTests extends IntegrationTest {

	@Test
	void scoresWithoutLlmAndRescoresWhenTheDefaultResumeChanges() throws Exception {
		long backend = uploadResume(TestFiles.pdfWithText("Desenvolvedor Java Senior Spring Boot Kafka AWS"), "a.pdf",
				"Backend Sênior");
		await(() -> count("select count(*) from resume_version where jsonb_array_length(skills) > 0") == 1);

		long jobId = createJob("Desenvolvedor Java Sênior", "Banco", "Requisitos: Java, Spring Boot, Kafka e AWS. Remoto.");
		await(() -> count("select count(*) from job_analysis where job_id = " + jobId) == 1);

		this.mvc.perform(get("/api/jobs/{id}", jobId).with(owner()))
			.andExpect(jsonPath("$.analysis.status").value("DETERMINISTICO"))
			.andExpect(jsonPath("$.analysis.matchScore").value(100))
			.andExpect(jsonPath("$.analysis.modelVersion").value("deterministico@v1"));

		long frontend = uploadResume(TestFiles.pdfWithText("Desenvolvedora React TypeScript"), "b.pdf", "Frontend Pleno");
		await(() -> count("select count(*) from resume_version where jsonb_array_length(skills) > 0") == 2);
		this.mvc.perform(patch("/api/resumes/{id}", frontend).with(owner())
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"makeDefault\": true}")).andExpect(status().isOk());

		await(() -> count("select match_score from job_analysis where job_id = " + jobId) < 50);
		assertThat(count("select resume_version_id from job_analysis where job_id = " + jobId)).isEqualTo(frontend);
		assertThat(backend).isNotEqualTo(frontend);

		this.mvc.perform(post("/api/jobs/{id}/analysis", jobId).with(owner())).andExpect(status().isNoContent());
	}

	private long count(String sql) {
		Long value = this.jdbc.sql(sql).query(Long.class).single();
		return (value == null) ? 0 : value;
	}

}
