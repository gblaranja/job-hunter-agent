package dev.laranja.jobhunter.insights;

import java.math.BigDecimal;

/** Uso da Claude API no mês por tarefa e modelo, com o custo estimado em dólares. */
public record LlmUsage(String task, String model, int calls, long inputTokens, long outputTokens, long cacheReadTokens,
		BigDecimal costUsd) {
}
