package dev.laranja.jobhunter.tailoring;

import dev.laranja.jobhunter.IntegrationTest;
import org.junit.jupiter.api.Test;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class TailoringWithoutAiIntegrationTests extends IntegrationTest {

	@Test
	void tailoringNeedsTheClaudeApi() throws Exception {
		this.mvc.perform(get("/api/tailored-resumes/availability").with(owner()))
			.andExpect(jsonPath("$.enabled").value(false))
			.andExpect(jsonPath("$.autoGenerate").value(false));
		long jobId = createJob("Desenvolvedor Java", "Banco Exemplo", "Java e Spring Boot.");

		this.mvc.perform(post("/api/jobs/{jobId}/tailored-resumes", jobId).with(owner()))
			.andExpect(status().isUnprocessableContent())
			.andExpect(jsonPath("$.detail").value(containsString("JOBHUNTER_AI_API_KEY")));
	}

}
