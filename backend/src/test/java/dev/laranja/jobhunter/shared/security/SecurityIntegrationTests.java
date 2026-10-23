package dev.laranja.jobhunter.shared.security;

import dev.laranja.jobhunter.IntegrationTest;
import org.junit.jupiter.api.Test;

import org.springframework.mock.web.MockHttpSession;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SecurityIntegrationTests extends IntegrationTest {

	@Test
	void apiRequiresLoginAndAnswers401InsteadOfRedirecting() throws Exception {
		this.mvc.perform(get("/api/jobs")).andExpect(status().isUnauthorized());
	}

	@Test
	void loginNeedsCsrfTokenAndCorrectPassword() throws Exception {
		this.mvc.perform(post("/api/auth/login").param("username", "admin").param("password", "jobhunter"))
			.andExpect(status().isForbidden());

		this.mvc
			.perform(post("/api/auth/login").param("username", "admin").param("password", "errada").with(csrf()))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.detail").value("Usuário ou senha inválidos."));
	}

	@Test
	void sessionCreatedByLoginOpensTheApi() throws Exception {
		MockHttpSession session = (MockHttpSession) this.mvc
			.perform(post("/api/auth/login").param("username", "admin").param("password", "jobhunter").with(csrf()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.username").value("admin"))
			.andReturn()
			.getRequest()
			.getSession();

		this.mvc.perform(get("/api/auth/me").session(session))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.username").value("admin"));
	}

	@Test
	void healthAndApiDocsArePublic() throws Exception {
		this.mvc.perform(get("/actuator/health")).andExpect(status().isOk());
		this.mvc.perform(get("/v3/api-docs"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.info.title").value("Job Hunter Agent API"));
	}

}
