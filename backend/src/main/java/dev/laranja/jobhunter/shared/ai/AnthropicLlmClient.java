package dev.laranja.jobhunter.shared.ai;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.util.List;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.errors.AnthropicException;
import com.anthropic.models.messages.CacheControlEphemeral;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.OutputConfig;
import com.anthropic.models.messages.StopReason;
import com.anthropic.models.messages.StructuredMessage;
import com.anthropic.models.messages.StructuredOutputConfig;
import com.anthropic.models.messages.StructuredTextBlock;
import com.anthropic.models.messages.TextBlockParam;
import com.anthropic.models.messages.Usage;
import io.micrometer.core.instrument.MeterRegistry;
import org.jspecify.annotations.Nullable;

import org.springframework.beans.factory.DisposableBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * Adapter da Claude API pelo SDK Java oficial. As instruções fixas vão como bloco de sistema com
 * {@code cache_control}, para o prefixo repetido (instruções e currículo) sair do cache; o uso de tokens de
 * cada chamada fica em {@code llm_usage}.
 */
@Component
@EnableConfigurationProperties(AiProperties.class)
class AnthropicLlmClient implements LlmClient, DisposableBean {

	private final AiProperties properties;

	private final JdbcClient jdbc;

	private final MeterRegistry meters;

	private final Clock clock;

	private final @Nullable AnthropicClient client;

	AnthropicLlmClient(AiProperties properties, JdbcClient jdbc, MeterRegistry meters, Clock clock) {
		this.properties = properties;
		this.jdbc = jdbc;
		this.meters = meters;
		this.clock = clock;
		this.client = properties.enabled() ? buildClient(properties) : null;
	}

	private static AnthropicClient buildClient(AiProperties properties) {
		AnthropicOkHttpClient.Builder builder = AnthropicOkHttpClient.builder()
			.apiKey(properties.apiKey())
			.maxRetries(2)
			.timeout(Duration.ofSeconds(90));
		if (properties.baseUrl() != null && !properties.baseUrl().isBlank()) {
			builder.baseUrl(properties.baseUrl());
		}
		return builder.build();
	}

	@Override
	public boolean enabled() {
		return this.client != null;
	}

	@Override
	public <T> T structured(LlmRequest request, Class<T> outputType) {
		if (this.client == null) {
			throw new LlmException("A Claude API não está configurada (JOBHUNTER_AI_API_KEY).");
		}
		StructuredOutputConfig<T> output = StructuredOutputConfig.<T>builder()
			.format(outputType)
			.effort(effort(request.effort()))
			.build();
		var params = MessageCreateParams.builder()
			.model(request.model())
			.maxTokens(request.maxTokens())
			.systemOfTextBlockParams(List.of(TextBlockParam.builder()
				.text(request.system())
				.cacheControl(CacheControlEphemeral.builder().build())
				.build()))
			.outputConfig(output)
			.addUserMessage(request.user())
			.build();
		StructuredMessage<T> response;
		try {
			response = this.client.messages().create(params);
		}
		catch (AnthropicException ex) {
			this.meters.counter("jobhunter.ai.errors", "task", request.task()).increment();
			throw new LlmException("Falha na chamada à Claude API: " + ex.getMessage(), ex);
		}
		recordUsage(request, response.usage());
		StopReason stopReason = response.stopReason().orElse(null);
		if (StopReason.MAX_TOKENS.equals(stopReason)) {
			throw new LlmException("A resposta foi cortada pelo limite de tokens.");
		}
		if (StopReason.REFUSAL.equals(stopReason)) {
			throw new LlmException("O modelo recusou a solicitação.");
		}
		return response.content()
			.stream()
			.flatMap(block -> block.text().stream())
			.map(StructuredTextBlock::text)
			.findFirst()
			.orElseThrow(() -> new LlmException("A resposta não trouxe o JSON esperado."));
	}

	private void recordUsage(LlmRequest request, Usage usage) {
		long cacheRead = usage.cacheReadInputTokens().orElse(0L);
		long cacheWrite = usage.cacheCreationInputTokens().orElse(0L);
		this.jdbc.sql("""
				insert into llm_usage (task, model, input_tokens, output_tokens, cache_read_tokens, cache_write_tokens, created_at)
				values (:task, :model, :input, :output, :cacheRead, :cacheWrite, :now)
				""")
			.param("task", request.task())
			.param("model", request.model())
			.param("input", usage.inputTokens())
			.param("output", usage.outputTokens())
			.param("cacheRead", cacheRead)
			.param("cacheWrite", cacheWrite)
			.param("now", Timestamp.from(this.clock.instant()))
			.update();
		this.meters.counter("jobhunter.ai.tokens", "task", request.task(), "type", "input").increment(usage.inputTokens());
		this.meters.counter("jobhunter.ai.tokens", "task", request.task(), "type", "output").increment(usage.outputTokens());
		this.meters.counter("jobhunter.ai.tokens", "task", request.task(), "type", "cache_read").increment(cacheRead);
	}

	private static OutputConfig.Effort effort(Effort effort) {
		return switch (effort) {
			case LOW -> OutputConfig.Effort.LOW;
			case MEDIUM -> OutputConfig.Effort.MEDIUM;
			case HIGH -> OutputConfig.Effort.HIGH;
		};
	}

	@Override
	public void destroy() {
		if (this.client != null) {
			this.client.close();
		}
	}

}
