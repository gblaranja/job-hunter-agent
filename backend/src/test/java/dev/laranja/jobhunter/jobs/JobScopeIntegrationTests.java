package dev.laranja.jobhunter.jobs;

import dev.laranja.jobhunter.IntegrationTest;
import org.junit.jupiter.api.Test;

import org.springframework.http.MediaType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class JobScopeIntegrationTests extends IntegrationTest {

	private static final String ENGLISH = """
			We are looking for a Senior Backend Engineer to join our team. You will build services in Java and
			work with the product team on the roadmap. This is a remote role for people in the Americas.
			""";

	private static final String PORTUGUESE = """
			Buscamos uma pessoa desenvolvedora backend para o nosso time. Você vai trabalhar com Java e Spring
			Boot, em contato com as áreas de produto e dados da empresa.
			""";

	@Test
	void inboxSplitsNationalAndInternationalJobsAndAcceptsCorrections() throws Exception {
		long national = createJob("Dev Java Sênior", "Banco A", PORTUGUESE);
		long international = createJob("Senior Backend Engineer", "Acme Inc", ENGLISH);

		this.mvc.perform(get("/api/jobs").param("inbox", "true").param("scope", "NACIONAL").with(owner()))
			.andExpect(jsonPath("$.total").value(1))
			.andExpect(jsonPath("$.items[0].id").value(national))
			.andExpect(jsonPath("$.items[0].scope").value("NACIONAL"));
		this.mvc.perform(get("/api/jobs").param("inbox", "true").param("scope", "INTERNACIONAL").with(owner()))
			.andExpect(jsonPath("$.total").value(1))
			.andExpect(jsonPath("$.items[0].id").value(international));
		this.mvc.perform(get("/api/jobs").param("inbox", "true").with(owner())).andExpect(jsonPath("$.total").value(2));

		this.mvc.perform(put("/api/jobs/{id}/scope", international).with(owner())
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"scope": "NACIONAL"}
					""")).andExpect(status().isNoContent());
		this.mvc.perform(get("/api/jobs/{id}", international).with(owner()))
			.andExpect(jsonPath("$.job.scope").value("NACIONAL"));
		// a correção fica travada contra reclassificações automáticas
		assertThat(this.jdbc.sql("select scope_locked from job where id = :id").param("id", international)
			.query(Boolean.class)
			.single()).isTrue();
		this.mvc.perform(get("/api/jobs").param("inbox", "true").param("scope", "INTERNACIONAL").with(owner()))
			.andExpect(jsonPath("$.total").value(0));

		this.mvc.perform(put("/api/jobs/{id}/scope", 999_999).with(owner())
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"scope": "NACIONAL"}
					""")).andExpect(status().isNotFound());
	}

	@Test
	void manualJobKeepsTheScopeYouChose() throws Exception {
		this.mvc.perform(post("/api/jobs").with(owner()).contentType(MediaType.APPLICATION_JSON).content("""
				{"title": "Dev Java", "company": "Empresa em Lisboa", "scope": "INTERNACIONAL",
				 "description": %s}
				""".formatted(json(PORTUGUESE))))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.scope").value("INTERNACIONAL"));
	}

}
