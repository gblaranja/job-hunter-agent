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
	 * Modelo menor para as tarefas frequentes (inclusive a verificação dos selos e a sugestão de nicho) e o maior só
	 * onde a qualidade do texto pesa: o relatório semanal e a reescrita do currículo para uma vaga (pedida vaga a
	 * vaga, não em lote). As entrevistas (preparação, histórias STAR e negociação da proposta) também usam o maior.
	 * {@code drafting} redige textos para a pessoa revisar (respostas de triagem e carta).
	 */
	public record Models(@DefaultValue("claude-haiku-5-5") String extraction,
			@DefaultValue("claude-haiku-5-5") String scoring, @DefaultValue("claude-haiku-5-5") String classification,
			@DefaultValue("claude-opus-5-5") String insights, @DefaultValue("claude-opus-5-5") String tailoring,
			@DefaultValue("claude-haiku-5-5") String recognition,
			@DefaultValue("claude-opus-5-5") String interviews, @DefaultValue("claude-opus-5-5") String drafting) {
	}

}
