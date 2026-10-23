package dev.laranja.jobhunter.shared.config;

import java.time.Clock;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class ApplicationConfig {

	@Bean
	Clock clock() {
		return Clock.systemUTC();
	}

	@Bean
	OpenAPI openApi() {
		return new OpenAPI().info(new Info().title("Job Hunter Agent API")
			.version("v1")
			.description("Backoffice do Job Hunter Agent: vagas, candidaturas e currículos."));
	}

}
