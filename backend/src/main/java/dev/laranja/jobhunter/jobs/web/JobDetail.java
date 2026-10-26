package dev.laranja.jobhunter.jobs.web;

import java.time.Instant;
import java.util.List;

import dev.laranja.jobhunter.jobs.DismissReason;
import dev.laranja.jobhunter.jobs.FeedbackRating;
import dev.laranja.jobhunter.jobs.FeedbackReason;
import dev.laranja.jobhunter.jobs.JobOrigin;
import dev.laranja.jobhunter.jobs.JobView;
import io.swagger.v3.oas.annotations.media.Schema;
import org.jspecify.annotations.Nullable;

/**
 * Detalhe da vaga com a candidatura (se houver) e a linha do tempo do funil.
 *
 * @param copies a mesma vaga publicada em outras fontes ("também publicada em")
 */
record JobDetail(JobView job, @Nullable ApplicationSummary application, List<TimelineEvent> timeline,
		@Nullable Feedback feedback, @Nullable Dismissal dismissal, @Nullable Analysis analysis, Legitimacy legitimacy,
		List<Copy> copies) {

	/**
	 * @param level ALTA, MEDIA, BAIXA ou SUSPEITA; nula antes da primeira avaliação
	 * @param duplicateOfJobId esta vaga é cópia da vaga indicada (a canônica)
	 */
	@Schema(name = "JobLegitimacy")
	record Legitimacy(dev.laranja.jobhunter.jobs.@Nullable Legitimacy level, List<String> reasons,
			@Nullable Long duplicateOfJobId) {
	}

	/**
	 * Outra publicação da mesma vaga.
	 * @param canonical é a publicação canônica; as demais são cópias dela
	 */
	@Schema(name = "JobCopy")
	record Copy(long id, JobOrigin origin, @Nullable String url, @Nullable Instant closedAt, boolean canonical) {
	}

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
