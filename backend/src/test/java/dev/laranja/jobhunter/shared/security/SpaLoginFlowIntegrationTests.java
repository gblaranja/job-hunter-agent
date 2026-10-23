package dev.laranja.jobhunter.shared.security;

import java.net.CookieManager;
import java.net.HttpCookie;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import dev.laranja.jobhunter.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * O fluxo de login como o navegador faz, contra o servidor real: o primeiro 401 entrega o cookie
 * {@code XSRF-TOKEN}, o login devolve o token no header e a sessão abre a API.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class SpaLoginFlowIntegrationTests {

	@LocalServerPort
	private int port;

	@Test
	void browserLikeLoginWithCsrfCookie() throws Exception {
		var cookies = new CookieManager();
		try (HttpClient http = HttpClient.newBuilder().cookieHandler(cookies).build()) {
			HttpResponse<String> anonymous = http.send(request("/api/auth/me").GET().build(),
					HttpResponse.BodyHandlers.ofString());
			assertThat(anonymous.statusCode()).isEqualTo(401);
			String csrfToken = xsrfCookie(cookies);
			assertThat(csrfToken).isNotBlank();

			HttpResponse<String> login = http.send(request("/api/auth/login")
				.header("Content-Type", "application/x-www-form-urlencoded")
				.header("X-XSRF-TOKEN", csrfToken)
				.POST(HttpRequest.BodyPublishers.ofString("username=admin&password=jobhunter"))
				.build(), HttpResponse.BodyHandlers.ofString());
			assertThat(login.statusCode()).isEqualTo(200);
			assertThat(login.body()).contains("\"username\":\"admin\"");

			HttpResponse<String> me = http.send(request("/api/auth/me").GET().build(),
					HttpResponse.BodyHandlers.ofString());
			assertThat(me.statusCode()).isEqualTo(200);

			HttpResponse<String> logout = http.send(request("/api/auth/logout").header("X-XSRF-TOKEN", xsrfCookie(cookies))
				.POST(HttpRequest.BodyPublishers.noBody())
				.build(), HttpResponse.BodyHandlers.ofString());
			assertThat(logout.statusCode()).isEqualTo(200);
			assertThat(http.send(request("/api/auth/me").GET().build(), HttpResponse.BodyHandlers.ofString())
				.statusCode()).isEqualTo(401);
		}
	}

	private HttpRequest.Builder request(String path) {
		return HttpRequest.newBuilder(URI.create("http://localhost:" + this.port + path));
	}

	private static String xsrfCookie(CookieManager cookies) {
		return cookies.getCookieStore()
			.getCookies()
			.stream()
			.filter(cookie -> cookie.getName().equals("XSRF-TOKEN"))
			.map(HttpCookie::getValue)
			.findFirst()
			.orElse(null);
	}

}
