package dev.laranja.jobhunter.jobs.web;

import java.time.Instant;
import java.util.List;

import dev.laranja.jobhunter.jobs.DismissReason;
import dev.laranja.jobhunter.jobs.FeedbackRating;
import dev.laranja.jobhunter.jobs.FeedbackReason;
import dev.laranja.jobhunter.jobs.JobView;
import io.swagger.v3.oas.annotations.media.Schema;
import org.jspecify.annotations.Nullable;

/** Detalhe da vaga com a candidatura (se houver) e a linha do tempo do funil. */
record JobDetail(JobView job, @Nullable ApplicationSummary application, List<TimelineEvent> timeline,
		@Nullable Feedback feedback, @Nullable Dismissal dismissal, @Nullable Analysis analysis) {

	/**
	 * @param nextActionKind FOLLOW_UP, RESPONDER, ENTREVISTA ou OUTRO
	 * @param nextActionAuto a próxima ação foi agendada pelo sistema (um retorno da empresa a cancela)
	 * @param followUpsSent follow-ups que você já registrou
	 */
	@Schema(name = "JobApplicationSummary")
	record ApplicationSummary(long id, String status, @Nullable Instant appliedAt, @Nullable String channel,
			@Nullable Long resumeVersionId, @Nullable String resumeLabel, @Nullable String notes,
			@Nullable Instant nextActionAt, @Nullable String nextActionKind, boolean nextActionAuto,
			int followUpsSent) {
	}

	@Schema(name = "JobFeedback")
	record Feedback(FeedbackRating rating, List<FeedbackReason> reasons, @Nullable String comment) {
	}

	/** A vaga foi descartada da caixa e por quê. */
	@Schema(name = "JobDismissal")
	record Dismissal(DismissReason reason, Instant dismissedAt) {
	}

	/**
	 * Análise da vaga.
	 * @param status DETERMINISTICO, PENDENTE, COMPLETO ou FALHOU
	 * @param partialSkills requisitos cobertos em parte por uma skill equivalente do currículo
	 */
	@Schema(name = "JobAnalysis")
	record Analysis(String status, @Nullable String seniority, List<String> requiredSkills, List<String> niceToHave,
			@Nullable String summary, @Nullable String employerKind, @Nullable String contractType,
			@Nullable Integer deterministicScore, @Nullable Integer llmAdjustment, @Nullable Integer matchScore,
			List<String> matchedSkills, List<String> gaps, @Nullable String justification, String modelVersion,
			@Nullable String lastError, Instant analyzedAt, List<PartialSkill> partialSkills) {
	}

	/** Requisito ({@code skill}) coberto em parte pela skill {@code via} do currículo. */
	@Schema(name = "JobPartialSkill")
	record PartialSkill(String skill, String via) {
	}

	record TimelineEvent(long id, @Nullable String fromStatus, @Nullable String toStatus,
			@Nullable String responseType, @Nullable String responseChannel, String origin, @Nullable String note,
			Instant createdAt) {
	}

}
