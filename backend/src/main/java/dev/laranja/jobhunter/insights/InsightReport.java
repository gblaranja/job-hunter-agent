package dev.laranja.jobhunter.insights;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.jspecify.annotations.Nullable;

/**
 * Relatório das últimas quatro semanas.
 *
 * @param aiAccuracy fração das classificações de e-mail da IA que você manteve na revisão
 * @param llmCostUsd custo da Claude API no mês do relatório, em dólares
 * @param recommendations sugestões para o currículo; geradas pelo LLM quando ele está configurado, ou pelas regras
 */
public record InsightReport(long id, LocalDate periodStart, LocalDate periodEnd, List<SkillDemand> topSkills,
		List<Gap> gaps, Funnel funnel, List<ResumePerformance> resumePerformance, List<SuggestedFilter> suggestedFilters,
		@Nullable BigDecimal aiAccuracy, @Nullable BigDecimal llmCostUsd, Recommendations recommendations,
		String modelVersion, Instant createdAt) {

	/** @param covered o currículo padrão tem a skill; nulo sem currículo com skills extraídas */
	public record SkillDemand(String skill, int jobs, @Nullable Boolean covered) {
	}

	/** @param avgScore score médio das vagas em que a skill faltou */
	public record Gap(String skill, int jobs, @Nullable Integer avgScore) {
	}

	/**
	 * Candidaturas desde o início, por etapa alcançada.
	 *
	 * @param avgScoreApplied score médio das vagas em que você se candidatou
	 * @param bySeniority conversão aplicada → entrevista por senioridade da vaga
	 * @param scoreByRating score médio das vagas por avaliação (GOSTEI, NEUTRO, NAO_GOSTEI)
	 */
	public record Funnel(int applied, int screening, int interview, int offer, @Nullable Integer avgScoreApplied,
			List<Conversion> bySeniority, Map<String, Integer> scoreByRating) {
	}

	public record Conversion(String group, int applied, int interview) {
	}

	public record ResumePerformance(long resumeVersionId, String label, int applied, int interview) {
	}

	/**
	 * Ajuste no perfil de busca sugerido pelos motivos de "não gostei".
	 *
	 * @param keyword termo para as exclusões, quando a sugestão é excluir algo
	 */
	public record SuggestedFilter(String reason, int count, String title, String detail, @Nullable String keyword) {
	}

	/**
	 * @param diagnosis CURRICULO (as vagas combinam mas não há retorno), FILTRO (você se candidata a vagas pouco
	 * aderentes), AMBOS, EQUILIBRADO ou POUCOS_DADOS
	 */
	public record Recommendations(List<GapAdvice> gaps, List<BulletRewrite> bullets, List<String> linkedinKeywords,
			String diagnosis, String diagnosisExplanation, String source) {
	}

	public record GapAdvice(String skill, String evidence, String suggestion) {
	}

	public record BulletRewrite(String original, String rewritten, String reason) {
	}

}
