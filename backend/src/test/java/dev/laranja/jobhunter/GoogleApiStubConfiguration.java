package dev.laranja.jobhunter;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistrar;

/**
 * Simula o OAuth do Google, a Gmail API, as APIs de vagas (ATS e agregadores), o Banco Central e a busca na web num
 * WireMock; os testes registram as respostas de que precisam.
 */
@TestConfiguration(proxyBeanMethods = false)
public class GoogleApiStubConfiguration {

	@Bean(destroyMethod = "stop")
	WireMockServer googleApi() {
		WireMockServer server = new WireMockServer(WireMockConfiguration.options().dynamicPort());
		server.start();
		return server;
	}

	@Bean
	DynamicPropertyRegistrar googleApiProperties(WireMockServer googleApi) {
		return registry -> {
			registry.add("jobhunter.gmail.client-id", () -> "test-client-id");
			registry.add("jobhunter.gmail.client-secret", () -> "test-client-secret");
			registry.add("jobhunter.gmail.auth-uri", () -> googleApi.baseUrl() + "/o/oauth2/v2/auth");
			registry.add("jobhunter.gmail.token-uri", () -> googleApi.baseUrl() + "/token");
			registry.add("jobhunter.gmail.api-base-url", googleApi::baseUrl);
			registry.add("jobhunter.gmail.after-connect-url", () -> "http://localhost:5173/settings");
			registry.add("jobhunter.sourcing.scheduler-enabled", () -> "false");
			registry.add("jobhunter.security.google.client-id", () -> "test-client-id");
			registry.add("jobhunter.security.google.client-secret", () -> "test-client-secret");
			registry.add("jobhunter.security.google.allowed-email", () -> "Dono@Example.com");
			registry.add("jobhunter.security.google.auth-uri", () -> googleApi.baseUrl() + "/o/oauth2/v2/auth");
			registry.add("jobhunter.security.google.token-uri", () -> googleApi.baseUrl() + "/token");
			registry.add("jobhunter.security.google.user-info-uri", () -> googleApi.baseUrl() + "/v1/userinfo");
			registry.add("jobhunter.security.google.after-login-url", () -> "http://localhost:5173/board");
			registry.add("jobhunter.security.google.login-url", () -> "http://localhost:5173/login");
			// Os ATS e as fontes amplas respondem no mesmo WireMock (os caminhos não colidem).
			registry.add("jobhunter.sourcing.ats.greenhouse-base-url", googleApi::baseUrl);
			registry.add("jobhunter.sourcing.ats.lever-base-url", googleApi::baseUrl);
			registry.add("jobhunter.sourcing.ats.ashby-base-url", googleApi::baseUrl);
			registry.add("jobhunter.sourcing.remotive.base-url", googleApi::baseUrl);
			registry.add("jobhunter.sourcing.github.base-url", googleApi::baseUrl);
			registry.add("jobhunter.sourcing.adzuna.base-url", googleApi::baseUrl);
			// Banco Central (OData) e a API de busca dos selos; a chave da busca só é ligada nos testes do GPTW.
			registry.add("jobhunter.niches.bcb.base-url", googleApi::baseUrl);
			registry.add("jobhunter.recognition.search.base-url", googleApi::baseUrl);
			registry.add("jobhunter.sourcing.linkedin-descriptions.base-url", googleApi::baseUrl);
			// Importação de vaga por link: as páginas vêm do WireMock local, que o bloqueio de rede interna barraria.
			registry.add("jobhunter.sourcing.job-import.allow-private-hosts", () -> "true");
			// Bot API do Telegram; o token e o chat id só são ligados nos testes do próprio canal.
			registry.add("jobhunter.notifications.telegram.base-url", googleApi::baseUrl);
			// Desligada por padrão (termos do LinkedIn); ligada aqui para os testes da própria busca.
			registry.add("jobhunter.sourcing.linkedin-descriptions.enabled", () -> "true");
		};
	}

}
