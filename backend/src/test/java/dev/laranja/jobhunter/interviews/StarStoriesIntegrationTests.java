package dev.laranja.jobhunter.interviews;

import com.jayway.jsonpath.JsonPath;
import dev.laranja.jobhunter.IntegrationTest;
import org.junit.jupiter.api.Test;

import org.springframework.http.MediaType;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class StarStoriesIntegrationTests extends IntegrationTest {

	@Test
	void keepsTheStoriesYouWrite() throws Exception {
		String created = this.mvc.perform(post("/api/stories").with(owner())
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"title": "  Migração do motor de crédito  ", "situation": "O motor caía no pico.",
					 "task": "Eu precisava estabilizar.", "action": "Movi o cálculo para eventos no Kafka.",
					 "result": "Zero quedas em 6 meses.", "skills": ["Kafka", "kafka", " Java ", ""]}
					"""))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.title").value("Migração do motor de crédito"))
			.andExpect(jsonPath("$.skills", contains("Kafka", "Java")))
			.andExpect(jsonPath("$.reflection").doesNotExist())
			.andReturn()
			.getResponse()
			.getContentAsString();
		long id = ((Number) JsonPath.read(created, "$.id")).longValue();

		this.mvc.perform(put("/api/stories/{id}", id).with(owner())
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"title": "Motor de crédito", "situation": "O motor caía no pico.", "action": "Eventos no Kafka.",
					 "result": "Zero quedas.", "reflection": "Teria medido antes.", "skills": ["Kafka"]}
					"""))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.task").value(""))
			.andExpect(jsonPath("$.reflection").value("Teria medido antes."));

		this.mvc.perform(get("/api/stories").with(owner()))
			.andExpect(jsonPath("$.length()").value(1))
			.andExpect(jsonPath("$[0].title").value("Motor de crédito"));

		// Sem a Claude API, lapidar não está disponível; o texto continua o seu.
		this.mvc.perform(post("/api/stories/polish").with(owner())
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"title": "Motor de crédito", "situation": "O motor caía."}
					"""))
			.andExpect(status().isUnprocessableContent())
			.andExpect(jsonPath("$.detail").value(containsString("JOBHUNTER_AI_API_KEY")));

		this.mvc.perform(post("/api/stories").with(owner())
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"title": " "}
					"""))
			.andExpect(status().isBadRequest());

		this.mvc.perform(delete("/api/stories/{id}", id).with(owner())).andExpect(status().isNoContent());
		this.mvc.perform(get("/api/stories/{id}", id).with(owner())).andExpect(status().isNotFound());
	}

}
