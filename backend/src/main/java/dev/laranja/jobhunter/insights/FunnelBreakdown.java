package dev.laranja.jobhunter.insights;

import java.util.List;

import org.jspecify.annotations.Nullable;

/**
 * O funil das candidaturas aberto pelo que mais pesa na conversão: de onde veio a vaga, por onde você aplicou, quanto
 * tempo levou para aplicar e se o currículo foi adaptado. Calculado na hora, sem LLM; a linha de base para medir se
 * as melhorias aumentam as entrevistas.
 *
 * @param applied candidaturas que chegaram a Aplicada ou além
 * @param medianDaysToFirstResponse mediana de dias entre aplicar e o primeiro retorno registrado
 */
public record FunnelBreakdown(int applied, int interview, int offer, List<FunnelGroup> bySource,
		List<FunnelGroup> byChannel, List<FunnelGroup> byTimeToApply, List<FunnelGroup> byTailoring,
		@Nullable Double medianDaysToFirstResponse, RankingQuality rankingQuality) {

	/** Candidaturas de um grupo e quantas chegaram à entrevista (ou teste técnico) e à oferta. */
	public record FunnelGroup(String group, int applied, int interview, int offer) {
	}

	/**
	 * Quanto o score acerta o que você escolhe. Entre as vagas que você já avaliou (positivas: com candidatura ou
	 * "gostei"; negativas: descartadas ou "não gostei"), as {@code topCount} de maior score e quantas eram positivas.
	 */
	public record RankingQuality(int evaluated, int positives, int topCount, int topPositives,
			@Nullable Integer avgScorePositive, @Nullable Integer avgScoreNegative) {
	}

}
