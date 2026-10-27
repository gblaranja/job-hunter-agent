package dev.laranja.jobhunter.shared.ai;

import java.math.BigDecimal;
import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Orçamento mensal da Claude API por tarefa ({@code extract-requirements}, {@code match-score}...), em dólares, sobre
 * o custo estimado em {@code llm_usage}. Tarefa sem valor não tem limite. Ao atingir, a chamada nem sai: a análise
 * fica no determinístico até o mês virar.
 *
 * @param monthlyUsd limite de cada tarefa no mês corrente
 */
@ConfigurationProperties("jobhunter.ai.budget")
public record LlmBudgetProperties(Map<String, BigDecimal> monthlyUsd) {

	public LlmBudgetProperties {
		monthlyUsd = (monthlyUsd == null) ? Map.of() : Map.copyOf(monthlyUsd);
	}

}
