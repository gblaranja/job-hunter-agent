package dev.laranja.jobhunter.shared.web;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import dev.laranja.jobhunter.shared.web.LinkVerifier.Result;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.ok;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

class LinkVerifierTest {

	private static final WireMockServer server = new WireMockServer(WireMockConfiguration.options().dynamicPort());

	private final LinkVerifier verifier = new LinkVerifier();

	@BeforeAll
	static void start() {
		server.start();
		server.stubFor(get("/vaga-ativa").willReturn(ok("<h1>Desenvolvedor Java</h1><button>Candidatar-se</button>")));
		server.stubFor(get("/vaga-encerrada").willReturn(ok("<p>Esta vaga não está mais disponível.</p>")));
		server.stubFor(get("/vaga-apagada").willReturn(aResponse().withStatus(404)));
		server.stubFor(get("/linkedin").willReturn(aResponse().withStatus(999)));
		server.stubFor(get("/redireciona").willReturn(aResponse().withStatus(302).withHeader("Location", "/login?next=/vaga")));
		server.stubFor(get(urlPathEqualTo("/login")).willReturn(ok("<form>Entrar</form>")));
	}

	@AfterAll
	static void stop() {
		server.stop();
	}

	@Test
	void distinguishesActiveRemovedAndIndeterminatePages() {
		assertThat(this.verifier.check(server.url("/vaga-ativa"))).isEqualTo(Result.ATIVO);
		assertThat(this.verifier.check(server.url("/vaga-encerrada"))).isEqualTo(Result.REMOVIDO);
		assertThat(this.verifier.check(server.url("/vaga-apagada"))).isEqualTo(Result.REMOVIDO);
		assertThat(this.verifier.check(server.url("/linkedin"))).isEqualTo(Result.INDETERMINADO);
		assertThat(this.verifier.check(server.url("/redireciona"))).isEqualTo(Result.INDETERMINADO);
		assertThat(this.verifier.check("http://localhost:1/nao-responde")).isEqualTo(Result.INDETERMINADO);
	}

}
