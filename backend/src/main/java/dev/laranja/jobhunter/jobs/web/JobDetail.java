package dev.laranja.jobhunter.jobs.web;

import java.time.Instant;
import java.util.List;

import dev.laranja.jobhunter.jobs.DismissReason;
import dev.laranja.jobhunter.jobs.FeedbackRating;
import dev.laranja.jobhunter.jobs.FeedbackReason;
import dev.laranja.jobhunter.jobs.InternationalEligibility;
import dev.laranja.jobhunter.jobs.JobFamily;
import dev.laranja.jobhunter.jobs.JobView;
import io.swagger.v3.oas.annotations.media.Schema;
import org.jspecify.annotations.Nullable;

/**
 * Detalhe da vaga com a candidatura (se houver) e a linha do tempo do funil.
 *
 * @param classification família de cargo e condições de contratação no exterior
 */
record JobDetail(JobView job, @Nullable ApplicationSummary application, List<TimelineEvent> timeline,
		@Nullable Feedback feedback, @Nullable Dismissal dismissal, @Nullable Analysis analysis,
		JobClassificationView classification) {

	@Schema(name = "JobApplicationSummary")
	record ApplicationSummary(long id, String status, @Nullable Instant appliedAt, @Nullable String channel,
			@Nullable Long resumeVersionId, @Nullable String resumeLabel, @Nullable String notes,
			@Nullable Instant nextActionAt) {
	}

	@Schema(name = "JobFeedback")
	record Feedback(FeedbackRating rating, List<FeedbackReason> reasons, @Nullable String comment) {
	}

	/**
	 * A vaga foi descartada da caixa e por quê.
	 * @param detail explicação do descarte automático (família de cargo fora dos perfis de busca); nulo no manual
	 */
	@Schema(name = "JobDismissal")
	record Dismissal(DismissReason reason, Instant dismissedAt, @Nullable String detail) {
	}

	/**
	 * Família de cargo e condições de contratação no exterior, pelas regras ou pela Claude.
	 * @param jobFamily nula quando nem as regras nem a Claude reconheceram a família
	 * @param internationalEligibility de onde a pessoa pode ser contratada; nula antes da classificação
	 * @param eligibleFromBrazil se dá para se candidatar morando no Brasil (não informado conta como sim)
	 * @param requiredTimezone fuso exigido, como aparece na vaga ("EST", "UTC-3")
	 * @param contractor contratação como contractor, sem vínculo de emprego
	 */
	@Schema(name = "JobClassification")
	record JobClassificationView(@Nullable JobFamily jobFamily,
			@Nullable InternationalEligibility internationalEligibility, boolean eligibleFromBrazil,
			@Nullable String requiredTimezone, boolean contractor) {
	}

	/**
	 * Análise da vaga.
	 * @param status DETERMINISTICO, PENDENTE, COMPLETO ou FALHOU
	 */
	@Schema(name = "JobAnalysis")
	record Analysis(String status, @Nullable String seniority, List<String> requiredSkills, List<String> niceToHave,
			@Nullable String summary, @Nullable String employerKind, @Nullable String contractType,
			@Nullable Integer deterministicScore, @Nullable Integer llmAdjustment, @Nullable Integer matchScore,
			List<String> matchedSkills, List<String> gaps, @Nullable String justification, String modelVersion,
			@Nullable String lastError, Instant analyzedAt) {
	}

	record TimelineEvent(long id, @Nullable String fromStatus, @Nullable String toStatus,
			@Nullable String responseType, @Nullable String responseChannel, String origin, @Nullable String note,
			Instant createdAt) {
	}

}
