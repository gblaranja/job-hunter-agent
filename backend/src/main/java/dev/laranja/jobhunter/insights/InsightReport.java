package dev.laranja.jobhunter.insights;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import dev.laranja.jobhunter.interviews.InterviewDebriefView.RecurringQuestion;
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
	 * @param recurringQuestions perguntas que apareceram em mais de um registro pós-entrevista; nulo nos relatórios
	 * anteriores a esse registro
	 */
	public record Funnel(int applied, int screening, int interview, int offer, @Nullable Integer avgScoreApplied,
			List<Conversion> bySeniority, Map<String, Integer> scoreByRating,
			@Nullable List<RecurringQuestion> recurringQuestions) {
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
	/**
	 * @param gupyProfile textos para os campos do perfil da Gupy; nulo nos relatórios antigos
	 * @param linkedinProfile headline, sobre e competências do LinkedIn; nulo nos relatórios antigos
	 * @param profileChanges o que mudou nesses textos desde o relatório anterior
	 */
	public record Recommendations(List<GapAdvice> gaps, List<BulletRewrite> bullets, List<String> linkedinKeywords,
			String diagnosis, String diagnosisExplanation, String source, @Nullable GupyProfile gupyProfile,
			@Nullable LinkedinProfile linkedinProfile, @Nullable List<String> profileChanges) {

		public Recommendations(List<GapAdvice> gaps, List<BulletRewrite> bullets, List<String> linkedinKeywords,
				String diagnosis, String diagnosisExplanation, String source) {
			this(gaps, bullets, linkedinKeywords, diagnosis, diagnosisExplanation, source, null, null, null);
		}

		public Recommendations withProfileChanges(List<String> changes) {
			return new Recommendations(this.gaps, this.bullets, this.linkedinKeywords, this.diagnosis,
					this.diagnosisExplanation, this.source, this.gupyProfile, this.linkedinProfile, changes);
		}

	}

	/**
	 * Textos para colar no perfil da Gupy, que o ATS dela lê junto com o currículo.
	 *
	 * @param skills campo "Habilidades": as skills separadas por vírgula
	 * @param experiences uma descrição por experiência do currículo
	 */
	public record GupyProfile(@JsonPropertyDescription("Campo Habilidades: skills que o currículo mostra, separadas por vírgula") String skills,
			@JsonPropertyDescription("Uma descrição por experiência do currículo") List<GupyExperience> experiences) {
	}

	public record GupyExperience(@JsonPropertyDescription("Cargo como está no currículo") String role,
			@JsonPropertyDescription("Empresa como está no currículo") String company,
			@JsonPropertyDescription("Descrição em até 600 caracteres, com tecnologias e resultados do currículo") String description) {
	}

	/** Textos para o perfil do LinkedIn, que recrutadores buscam por palavra-chave. */
	public record LinkedinProfile(@JsonPropertyDescription("Título em até 220 caracteres: cargo-alvo e as skills mais pedidas que a pessoa tem") String headline,
			@JsonPropertyDescription("Seção Sobre em até 1500 caracteres, em primeira pessoa") @Nullable String about,
			@JsonPropertyDescription("Até 10 competências para a seção de competências") List<String> skills) {
	}

	public record GapAdvice(String skill, String evidence, String suggestion) {
	}

	public record BulletRewrite(String original, String rewritten, String reason) {
	}

}
