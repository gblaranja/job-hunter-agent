package dev.laranja.jobhunter.shared.ai;

import org.jspecify.annotations.Nullable;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Integração com a Claude API. Sem {@code apiKey}, a análise roda só na camada determinística.
 *
 * @param baseUrl sobrescreve o endereço da API (testes)
 * @param models modelo de cada tarefa
 */
@ConfigurationProperties("jobhunter.ai")
public record AiProperties(@Nullable String apiKey, @Nullable String baseUrl, @DefaultValue Models models) {

	public boolean enabled() {
		return this.apiKey != null && !this.apiKey.isBlank();
	}

	/**
	 * Modelo menor para as tarefas frequentes e o maior só no relatório semanal, como no documento de arquitetura.
	 */
	public record Models(@DefaultValue("claude-haiku-5-5") String extraction,
			@DefaultValue("claude-haiku-5-5") String scoring, @DefaultValue("claude-haiku-5-5") String classification,
			@DefaultValue("claude-opus-5-5") String insights) {
	}

}
