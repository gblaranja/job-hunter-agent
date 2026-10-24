package dev.laranja.jobhunter.recognition;

import org.jspecify.annotations.Nullable;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Selos das empresas. Sem a chave da API de busca, nada é buscado; a importação em lote continua funcionando.
 *
 * @param reviewThreshold abaixo desta confiança o selo vai para a fila de revisão
 * @param recheckAfterDays depois deste prazo a empresa é verificada de novo (job mensal)
 * @param recheckCron job mensal que reverifica as empresas com a verificação vencida
 */
@ConfigurationProperties("jobhunter.recognition")
public record RecognitionProperties(@DefaultValue Search search, @DefaultValue("0.8") double reviewThreshold,
		@DefaultValue("180") int recheckAfterDays, @DefaultValue("0 0 4 2 * *") String recheckCron) {

	/**
	 * API de busca na web. Só o Tavily tem adapter por enquanto; Brave e Serper entram atrás da mesma porta.
	 *
	 * @param monthlyLimit consultas pagas por mês (as respostas em cache não contam); configure também o limite de
	 * gasto no painel do provedor
	 * @param cacheDays validade do cache das respostas
	 */
	public record Search(@DefaultValue("tavily") String provider, @Nullable String apiKey,
			@DefaultValue("https://api.tavily.com") String baseUrl, @DefaultValue("200") int monthlyLimit,
			@DefaultValue("180") int cacheDays, @DefaultValue("5") int maxResults) {

		public boolean enabled() {
			return this.apiKey != null && !this.apiKey.isBlank();
		}

	}

}
