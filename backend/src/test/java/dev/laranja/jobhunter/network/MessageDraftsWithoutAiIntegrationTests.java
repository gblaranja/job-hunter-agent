package dev.laranja.jobhunter.network;

import dev.laranja.jobhunter.IntegrationTest;
import org.junit.jupiter.api.Test;

import org.springframework.http.MediaType;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Sem a Claude API, os rascunhos saem de modelos fixos no idioma da vaga. */
class MessageDraftsWithoutAiIntegrationTests extends IntegrationTest {

	@Test
	void usesAFixedTemplateInTheLanguageOfTheJob() throws Exception {
		long jobId = createJob("Backend Engineer", "Acme Inc",
				"We are looking for an engineer with experience in Java and Kafka. You will work with our team.");

		this.mvc.perform(post("/api/jobs/{id}/drafts", jobId).with(owner())
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"kind\": \"FOLLOW_UP\"}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.source").value("MODELO"))
			.andExpect(jsonPath("$.language").value("en"))
			.andExpect(jsonPath("$.text").value(containsString("Backend Engineer position at Acme Inc")))
			.andExpect(jsonPath("$.linkedinNote").doesNotExist());
	}

}
