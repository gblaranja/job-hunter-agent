package dev.laranja.jobhunter.shared.security;

import java.time.Instant;

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

/**
 * O {@code state} não depende de cookie/sessão (é o instante cifrado), por isso cada chamada aqui é independente,
 * como duas abas diferentes do navegador seriam na vida real.
 */
class GoogleLoginIntegrationTests extends IntegrationTest {

	@Autowired
	private WireMockServer google;

	@Autowired
	private SecretCipher cipher;

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
		String state = start();

		MvcResult result = this.mvc
			.perform(get("/api/auth/google/callback").param("code", "codigo-ok").param("state", state))
			.andExpect(redirectedUrl("http://localhost:5173/board"))
			.andReturn();

		this.mvc.perform(get("/api/auth/me").session((MockHttpSession) result.getRequest().getSession()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.username").value("dono@example.com"))
			.andExpect(jsonPath("$.displayName").value("Dono"));
		this.google.verify(WireMock.getRequestedFor(WireMock.urlEqualTo("/v1/userinfo"))
			.withHeader("Authorization", equalTo("Bearer login-token")));
	}

	@Test
	void anotherAccountIsRefused() throws Exception {
		stubUser("outra@example.com", true);
		String state = start();

		this.mvc.perform(get("/api/auth/google/callback").param("code", "codigo-ok").param("state", state))
			.andExpect(redirectedUrl("http://localhost:5173/login?google=negado"));
	}

	@Test
	void unverifiedEmailIsRefused() throws Exception {
		stubUser("dono@example.com", false);
		String state = start();

		this.mvc.perform(get("/api/auth/google/callback").param("code", "codigo-ok").param("state", state))
			.andExpect(redirectedUrl("http://localhost:5173/login?google=negado"));
	}

	@Test
	void aForgedStateIsRejectedWithoutCallingGoogle() throws Exception {
		this.mvc.perform(get("/api/auth/google/callback").param("code", "codigo-ok").param("state", "forjado"))
			.andExpect(redirectedUrl("http://localhost:5173/login?google=erro"));
		assertThat(this.google.findAll(WireMock.postRequestedFor(WireMock.urlEqualTo("/token")))).isEmpty();
	}

	/** Um state de mais de 10 minutos (por exemplo, uma aba de login esquecida aberta) não serve mais. */
	@Test
	void anExpiredStateIsRejected() throws Exception {
		String expired = this.cipher.encrypt(String.valueOf(Instant.now().minusSeconds(11 * 60).toEpochMilli()));

		this.mvc.perform(get("/api/auth/google/callback").param("code", "codigo-ok").param("state", expired))
			.andExpect(redirectedUrl("http://localhost:5173/login?google=erro"));
	}

	/** Começa o fluxo (sem sessão nenhuma) e devolve o state que o Google mandaria de volta. */
	private String start() throws Exception {
		String location = this.mvc.perform(get("/api/auth/google"))
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
