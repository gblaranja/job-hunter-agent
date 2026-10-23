package dev.laranja.jobhunter.inbox;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import dev.laranja.jobhunter.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.web.util.UriComponentsBuilder;

import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GmailConnectionIntegrationTests extends IntegrationTest {

	@Autowired
	private WireMockServer google;

	@BeforeEach
	void resetGoogle() {
		this.google.resetAll();
	}

	@Test
	void connectsThroughGoogleConsentAndStoresTokensEncrypted() throws Exception {
		this.google.stubFor(WireMock.post("/token").willReturn(okJson("""
				{"access_token": "access-1", "expires_in": 3599, "refresh_token": "refresh-1",
				 "scope": "https://www.googleapis.com/auth/gmail.readonly"}
				""")));
		this.google.stubFor(WireMock.get("/gmail/v1/users/me/profile").willReturn(okJson("""
				{"emailAddress": "eu@example.com", "historyId": "1"}
				""")));
		MockHttpSession session = new MockHttpSession();

		String consentUrl = this.mvc.perform(get("/api/admin/gmail/connect").session(session).with(owner()))
			.andExpect(status().isFound())
			.andReturn()
			.getResponse()
			.getRedirectedUrl();
		var consent = UriComponentsBuilder.fromUriString(consentUrl).build();
		assertThat(consent.getPath()).isEqualTo("/o/oauth2/v2/auth");
		assertThat(consent.getQueryParams().getFirst("access_type")).isEqualTo("offline");
		assertThat(consent.getQueryParams().getFirst("prompt")).isEqualTo("consent");
		String state = consent.getQueryParams().getFirst("state");

		this.mvc.perform(get("/api/admin/gmail/callback").param("code", "auth-code").param("state", state)
			.session(session)
			.with(owner())).andExpect(redirectedUrl("http://localhost:5173/settings?gmail=conectado"));

		this.google.verify(postRequestedFor(urlPathEqualTo("/token")).withRequestBody(containing("code=auth-code"))
			.withRequestBody(containing("grant_type=authorization_code")));
		String storedRefresh = this.jdbc.sql("select refresh_token_enc from oauth_credential").query(String.class).single();
		assertThat(storedRefresh).isNotBlank().doesNotContain("refresh-1");
		this.mvc.perform(get("/api/admin/gmail/status").with(owner()))
			.andExpect(jsonPath("$.configured").value(true))
			.andExpect(jsonPath("$.connected").value(true))
			.andExpect(jsonPath("$.accountEmail").value("eu@example.com"))
			.andExpect(jsonPath("$.label").value("jobs"));

		this.mvc.perform(delete("/api/admin/gmail").with(owner())).andExpect(status().isNoContent());
		this.mvc.perform(get("/api/admin/gmail/status").with(owner())).andExpect(jsonPath("$.connected").value(false));
	}

	@Test
	void callbackWithAForgedStateIsRejected() throws Exception {
		MockHttpSession session = new MockHttpSession();
		this.mvc.perform(get("/api/admin/gmail/connect").session(session).with(owner()));

		this.mvc.perform(get("/api/admin/gmail/callback").param("code", "auth-code").param("state", "forjado")
			.session(session)
			.with(owner())).andExpect(redirectedUrl("http://localhost:5173/settings?gmail=erro"));

		assertThat(this.jdbc.sql("select count(*) from oauth_credential").query(Long.class).single()).isZero();
	}

}
