package dev.laranja.jobhunter;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistrar;

/** Simula o OAuth do Google e a Gmail API num WireMock; os testes registram as respostas de que precisam. */
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
		};
	}

}
