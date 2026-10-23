package dev.laranja.jobhunter;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistrar;

/** Liga a IA apontando para uma Claude API simulada no WireMock. Usado só nos testes da análise com LLM. */
@TestConfiguration(proxyBeanMethods = false)
public class AnthropicStubConfiguration {

	@Bean(destroyMethod = "stop")
	WireMockServer anthropicApi() {
		WireMockServer server = new WireMockServer(WireMockConfiguration.options().dynamicPort());
		server.start();
		return server;
	}

	@Bean
	DynamicPropertyRegistrar anthropicProperties(WireMockServer anthropicApi) {
		return registry -> {
			registry.add("jobhunter.ai.api-key", () -> "test-api-key");
			registry.add("jobhunter.ai.base-url", anthropicApi::baseUrl);
		};
	}

	/** Resposta da Messages API com o JSON da saída estruturada como texto. */
	public static String messageWith(String json) {
		String escaped = json.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", " ");
		return """
				{"id": "msg_test", "type": "message", "role": "assistant", "model": "claude-haiku-5-5",
				 "content": [{"type": "text", "text": "%s"}], "stop_reason": "end_turn", "stop_sequence": null,
				 "usage": {"input_tokens": 1200, "output_tokens": 90, "cache_creation_input_tokens": 0,
				           "cache_read_input_tokens": 1000}}
				""".formatted(escaped);
	}

}
