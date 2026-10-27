package dev.laranja.jobhunter.interviews;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import dev.laranja.jobhunter.tracking.ApplicationStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import org.jspecify.annotations.Nullable;

@Schema(name = "InterviewDebriefView")
public record InterviewDebriefView(long id, long applicationId, ApplicationStatus stage, LocalDate interviewedOn,
		List<String> questions, @Nullable String wentWell, @Nullable String toImprove, @Nullable Integer rating,
		@Nullable String nextSteps, Instant createdAt) {

	/**
	 * O registro salvo e o follow-up agendado na candidatura.
	 * @param followUpAt próximo contato com a empresa, 5 dias úteis depois da entrevista; nulo se a candidatura já
	 * está encerrada
	 */
	@Schema(name = "InterviewDebriefSaved")
	public record Saved(InterviewDebriefView debrief, @Nullable Instant followUpAt) {
	}

	/** Pergunta que apareceu em mais de uma entrevista, com o texto do registro mais recente. */
	@Schema(name = "RecurringInterviewQuestion")
	public record RecurringQuestion(String question, int count) {
	}

}
