package dev.laranja.jobhunter.interviews.internal;

import java.util.List;

import dev.laranja.jobhunter.interviews.InterviewDebriefView.RecurringQuestion;
import dev.laranja.jobhunter.profile.CandidateProfileView;
import dev.laranja.jobhunter.tracking.ApplicationStatus;
import org.jspecify.annotations.Nullable;

/** Tudo o que a preparação lê: a vaga aplicada, a análise, a empresa, as suas histórias e as etapas anteriores. */
public record PrepInputs(long applicationId, long jobId, ApplicationStatus stage, String title, String company,
		@Nullable String description, @Nullable String location, String workModel, @Nullable String salaryRange,
		@Nullable String seniority, List<String> requiredSkills, List<String> niceToHave, List<String> matchedSkills,
		List<String> gaps, @Nullable String summary, @Nullable String contractType, @Nullable String employerKind,
		List<Knockout> knockouts, List<Evidence> evidence, Company companyFacts, List<Story> stories,
		List<PastDebrief> debriefs, List<RecurringQuestion> recurringQuestions, CandidateProfileView profile) {

	/** Eliminatório da vaga avaliado contra o seu perfil (ATENDE, NAO_ATENDE ou SEM_INFORMACAO). */
	public record Knockout(String type, String text, @Nullable String status, @Nullable String reason) {
	}

	/** Trecho do currículo que comprova um requisito. */
	public record Evidence(String requirement, String quote) {
	}

	public record Company(@Nullable String kind, @Nullable String sector, @Nullable String sizeRange,
			@Nullable String hqLocation, @Nullable String website, @Nullable String watchStatus,
			@Nullable String gptw) {
	}

	public record Story(long id, String title, List<String> skills, String situation, String action, String result) {
	}

	public record PastDebrief(ApplicationStatus stage, String interviewedOn, List<String> questions,
			@Nullable String toImprove, @Nullable String nextSteps) {
	}

}
