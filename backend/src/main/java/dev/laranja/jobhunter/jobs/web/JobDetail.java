package dev.laranja.jobhunter.jobs.web;

import java.time.Instant;
import java.util.List;

import dev.laranja.jobhunter.jobs.FeedbackRating;
import dev.laranja.jobhunter.jobs.FeedbackReason;
import dev.laranja.jobhunter.jobs.JobView;
import io.swagger.v3.oas.annotations.media.Schema;
import org.jspecify.annotations.Nullable;

/** Detalhe da vaga com a candidatura (se houver) e a linha do tempo do funil. */
record JobDetail(JobView job, @Nullable ApplicationSummary application, List<TimelineEvent> timeline,
		@Nullable Feedback feedback, @Nullable Analysis analysis) {

	@Schema(name = "JobApplicationSummary")
	record ApplicationSummary(long id, String status, @Nullable Instant appliedAt, @Nullable String channel,
			@Nullable Long resumeVersionId, @Nullable String resumeLabel, @Nullable String notes,
			@Nullable Instant nextActionAt) {
	}

	@Schema(name = "JobFeedback")
	record Feedback(FeedbackRating rating, List<FeedbackReason> reasons, @Nullable String comment) {
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
			@Nullable String responseType, String origin, @Nullable String note, Instant createdAt) {
	}

}
