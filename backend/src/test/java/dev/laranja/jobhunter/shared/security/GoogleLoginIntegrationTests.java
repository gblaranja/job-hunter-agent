package dev.laranja.jobhunter.shared.security;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import dev.laranja.jobhunter.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.util.UriComponentsBuilder;

import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GoogleLoginIntegrationTests extends IntegrationTest {

	@Autowired
	private WireMockServer google;

	@BeforeEach
	void reset() {
		this.google.resetAll();
		this.google.stubFor(WireMock.post("/token").withRequestBody(containing("code=codigo-ok"))
			.willReturn(okJson("{\"access_token\": \"login-token\", \"expires_in\": 3599}")));
	}

	@Test
	void providersArePublic() throws Exception {
		this.mvc.perform(get("/api/auth/providers"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.password").value(true))
			.andExpect(jsonPath("$.google").value(true));
	}

	@Test
	void theAllowedVerifiedAccountGetsASession() throws Exception {
		stubUser("dono@example.com", true);
		MockHttpSession session = new MockHttpSession();
		String state = start(session);

		MvcResult result = this.mvc.perform(get("/api/auth/google/callback").session(session)
			.param("code", "codigo-ok")
			.param("state", state)).andExpect(redirectedUrl("http://localhost:5173/board")).andReturn();

		MockHttpSession signedIn = (MockHttpSession) result.getRequest().getSession();
		this.mvc.perform(get("/api/auth/me").session(signedIn))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.username").value("admin"));
		this.google.verify(WireMock.getRequestedFor(WireMock.urlEqualTo("/v1/userinfo"))
			.withHeader("Authorization", equalTo("Bearer login-token")));
	}

	@Test
	void anotherAccountIsRefused() throws Exception {
		stubUser("outra@example.com", true);
		MockHttpSession session = new MockHttpSession();
		String state = start(session);

		this.mvc.perform(get("/api/auth/google/callback").session(session).param("code", "codigo-ok").param("state", state))
			.andExpect(redirectedUrl("http://localhost:5173/login?google=negado"));
		this.mvc.perform(get("/api/auth/me").session(session)).andExpect(status().isUnauthorized());
	}

	@Test
	void unverifiedEmailIsRefused() throws Exception {
		stubUser("dono@example.com", false);
		MockHttpSession session = new MockHttpSession();
		String state = start(session);

		this.mvc.perform(get("/api/auth/google/callback").session(session).param("code", "codigo-ok").param("state", state))
			.andExpect(redirectedUrl("http://localhost:5173/login?google=negado"));
	}

	@Test
	void aForgedStateIsRejectedWithoutCallingGoogle() throws Exception {
		MockHttpSession session = new MockHttpSession();
		start(session);

		this.mvc.perform(get("/api/auth/google/callback").session(session).param("code", "codigo-ok").param("state", "forjado"))
			.andExpect(redirectedUrl("http://localhost:5173/login?google=erro"));
		assertThat(this.google.findAll(WireMock.postRequestedFor(WireMock.urlEqualTo("/token")))).isEmpty();
	}

	/** Começa o fluxo e devolve o state que o Google mandaria de volta. */
	private String start(MockHttpSession session) throws Exception {
		String location = this.mvc.perform(get("/api/auth/google").session(session))
			.andExpect(status().isFound())
			.andReturn()
			.getResponse()
			.getRedirectedUrl();
		assertThat(location).startsWith(this.google.baseUrl() + "/o/oauth2/v2/auth")
			.contains("scope=openid%20email%20profile")
			.contains("redirect_uri=");
		return UriComponentsBuilder.fromUriString(location).build().getQueryParams().getFirst("state");
	}

	private void stubUser(String email, boolean verified) {
		this.google.stubFor(WireMock.get("/v1/userinfo").willReturn(okJson("""
				{"sub": "123", "email": "%s", "email_verified": %s, "name": "Dono"}
				""".formatted(email, verified))));
	}

}
