package dev.laranja.jobhunter.shared.security;

import com.jayway.jsonpath.JsonPath;
import dev.laranja.jobhunter.IntegrationTest;
import org.junit.jupiter.api.Test;

import org.springframework.http.MediaType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PersonalApiTokenIntegrationTests extends IntegrationTest {

	private static final String JOB = """
			{"title": "Dev Java", "company": "Acme", "url": "https://acme.com/vagas/1", "description": "Spring"}
			""";

	@Test
	void theTokenOnlySavesJobsAndStopsWorkingWhenRevoked() throws Exception {
		String token = JsonPath.read(this.mvc.perform(post("/api/auth/api-token").with(owner()))
			.andExpect(status().isCreated())
			.andReturn()
			.getResponse()
			.getContentAsString(), "$.token");
		assertThat(token).startsWith("jh_").hasSizeGreaterThan(40);
		// Só o hash fica gravado.
		assertThat(this.jdbc.sql("select token_hash from personal_api_token").query(String.class).single())
			.hasSize(64)
			.doesNotContain(token.substring(3));

		// Sem sessão e sem CSRF, só com o token.
		this.mvc.perform(post("/api/jobs").header("Authorization", "Bearer " + token)
			.contentType(MediaType.APPLICATION_JSON)
			.content(JOB)).andExpect(status().isCreated());
		// A prévia da importação também aceita o token (a extensão manda o texto selecionado da página aberta).
		this.mvc.perform(post("/api/jobs/import/preview").header("Authorization", "Bearer " + token)
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"url": "https://www.linkedin.com/jobs/view/123", "text": "Desenvolvedor Java na Acme. Spring Boot."}
					""")).andExpect(status().isOk());
		this.mvc.perform(get("/api/auth/api-token").with(owner()))
			.andExpect(jsonPath("$.exists").value(true))
			.andExpect(jsonPath("$.hint").value(token.substring(token.length() - 4)))
			.andExpect(jsonPath("$.lastUsedAt").isNotEmpty());

		// Qualquer outro endpoint recusa o token, inclusive o que gera tokens.
		this.mvc.perform(get("/api/jobs").header("Authorization", "Bearer " + token)).andExpect(status().isUnauthorized());
		// Fora da cadeia do token, um POST sem cookie de CSRF para no CSRF.
		this.mvc.perform(post("/api/auth/api-token").header("Authorization", "Bearer " + token))
			.andExpect(status().isForbidden());
		this.mvc.perform(post("/api/jobs").header("Authorization", "Bearer jh_errado")
			.contentType(MediaType.APPLICATION_JSON)
			.content(JOB)).andExpect(status().isUnauthorized());

		this.mvc.perform(delete("/api/auth/api-token").with(owner())).andExpect(status().isNoContent());
		this.mvc.perform(post("/api/jobs").header("Authorization", "Bearer " + token)
			.contentType(MediaType.APPLICATION_JSON)
			.content(JOB)).andExpect(status().isUnauthorized());
		this.mvc.perform(get("/api/auth/api-token").with(owner())).andExpect(jsonPath("$.exists").value(false));
	}

}
