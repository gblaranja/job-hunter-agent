package dev.laranja.jobhunter.analysis.internal;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import dev.laranja.jobhunter.analysis.Seniority;
import dev.laranja.jobhunter.analysis.internal.LlmOutputs.ScoreAdjustment;
import dev.laranja.jobhunter.jobs.JobView;
import dev.laranja.jobhunter.resumes.ResumeCatalog.ResumeProfile;
import dev.laranja.jobhunter.shared.ai.AiProperties;
import dev.laranja.jobhunter.shared.ai.LlmClient;
import dev.laranja.jobhunter.shared.ai.LlmClient.Effort;
import dev.laranja.jobhunter.shared.ai.LlmBudgetExceededException;
import dev.laranja.jobhunter.shared.ai.LlmClient.LlmRequest;
import dev.laranja.jobhunter.shared.ai.LlmException;
import org.jspecify.annotations.Nullable;

import org.springframework.stereotype.Component;

/** Executa uma análise: requisitos, score determinístico e, com o LLM, a extração refinada e o ajuste do score. */
@Component
public class AnalysisEngine {

	private static final String DETERMINISTIC_VERSION = "deterministico@v1";

	private static final int RESUME_CHARACTERS = 20_000;

	private final RequirementsExtractor extractor;

	private final DeterministicScorer scorer;

	private final SkillDictionary dictionary;

	private final LlmClient llm;

	private final AiProperties ai;

	AnalysisEngine(RequirementsExtractor extractor, DeterministicScorer scorer, SkillDictionary dictionary,
			LlmClient llm, AiProperties ai) {
		this.extractor = extractor;
		this.scorer = scorer;
		this.dictionary = dictionary;
		this.llm = llm;
		this.ai = ai;
	}

	/**
	 * Análise completa. Reaproveita os requisitos já extraídos pelo LLM com o prompt atual, para não pagar a
	 * extração de novo numa repontuação.
	 */
	public Outcome run(JobView job, Optional<ResumeProfile> resume, Optional<StoredRequirements> previous,
			List<FeedbackExample> feedback) {
		JobRequirements requirements;
		String version;
		String status;
		String error = null;
		Optional<StoredRequirements> reusable = previous
			.filter(stored -> stored.modelVersion() != null && stored.modelVersion().contains(this.extractor.extractionVersion()));
		if (reusable.isPresent()) {
			requirements = reusable.get().toRequirements();
			version = this.extractor.extractionVersion();
			status = "COMPLETO";
		}
		else if (this.extractor.canUseLlm(job)) {
			try {
				requirements = this.extractor.withLlm(job);
				version = this.extractor.extractionVersion();
				status = "COMPLETO";
			}
			catch (LlmBudgetExceededException ex) {
				// Orçamento do mês acabou: fica no determinístico e o agendador completa quando houver orçamento.
				requirements = this.extractor.deterministic(job);
				version = DETERMINISTIC_VERSION;
				status = "PENDENTE";
				error = ex.getMessage();
			}
			catch (LlmException ex) {
				requirements = this.extractor.deterministic(job);
				version = DETERMINISTIC_VERSION;
				status = "FALHOU";
				error = ex.getMessage();
			}
		}
		else {
			requirements = this.extractor.deterministic(job);
			version = DETERMINISTIC_VERSION;
			status = "DETERMINISTICO";
		}

		if (resume.isEmpty()) {
			return new Outcome(status, requirements, null, null, null, List.of(), List.of(), null, version, error);
		}
		DeterministicScorer.Result base = this.scorer.score(requirements, resumeSkills(resume.get()),
				targetSeniority(resume.get()));
		Integer adjustment = null;
		String justification = null;
		if ("COMPLETO".equals(status)) {
			try {
				ScoreAdjustment adjusted = adjust(job, requirements, base, resume.get(), feedback);
				adjustment = adjusted.clamped();
				justification = adjusted.justification();
				version = version + "+" + this.ai.models().scoring() + "|" + Prompts.MATCH_SCORE.id();
			}
			catch (LlmBudgetExceededException ex) {
				status = "PENDENTE";
				error = ex.getMessage();
			}
			catch (LlmException ex) {
				status = "FALHOU";
				error = ex.getMessage();
			}
		}
		int score = clamp(base.score() + ((adjustment != null) ? adjustment : 0));
		return new Outcome(status, requirements, base.score(), adjustment, score, base.matchedSkills(), base.gaps(),
				justification, version, error);
	}

	/** Só a parte determinística, sobre os requisitos já salvos; o ajuste do LLM fica pendente para depois. */
	public Outcome deterministicRescore(JobView job, Optional<ResumeProfile> resume,
			Optional<StoredRequirements> previous) {
		// Requisitos do LLM são reaproveitados com a grafia do dicionário atual; os determinísticos são extraídos de
		// novo, que é barato e pega as skills aceitas na revisão desde a última análise.
		JobRequirements requirements = previous
			.filter(stored -> stored.modelVersion() != null && !stored.modelVersion().equals(DETERMINISTIC_VERSION))
			.map(StoredRequirements::toRequirements)
			.map(this::withDictionarySpelling)
			.orElseGet(() -> this.extractor.deterministic(job));
		String version = previous.map(StoredRequirements::modelVersion).orElse(DETERMINISTIC_VERSION);
		boolean llmPending = this.llm.enabled() && version.contains(this.extractor.extractionVersion());
		String status = llmPending ? "PENDENTE" : "DETERMINISTICO";
		if (resume.isEmpty()) {
			return new Outcome(status, requirements, null, null, null, List.of(), List.of(), null, version, null);
		}
		DeterministicScorer.Result base = this.scorer.score(requirements, resumeSkills(resume.get()),
				targetSeniority(resume.get()));
		return new Outcome(status, requirements, base.score(), null, base.score(), base.matchedSkills(), base.gaps(),
				null, version, null);
	}

	/** Só o score determinístico, para um conjunto de skills qualquer (a prévia de um currículo adaptado). */
	public Fit fit(StoredRequirements requirements, List<String> resumeSkills, @Nullable String targetRole) {
		DeterministicScorer.Result result = this.scorer.score(requirements.toRequirements(), resumeSkills,
				Seniority.detect(targetRole));
		return new Fit(result.score(), result.matchedSkills(), result.gaps());
	}

	private ScoreAdjustment adjust(JobView job, JobRequirements requirements, DeterministicScorer.Result base,
			ResumeProfile resume, List<FeedbackExample> feedback) {
		// O currículo fica no bloco de sistema (em cache); vaga, cálculo e feedback variam a cada chamada.
		String resumeText = (resume.text() == null) ? String.join(", ", resume.skills())
				: resume.text().substring(0, Math.min(RESUME_CHARACTERS, resume.text().length()));
		String system = Prompts.MATCH_SCORE.text() + "\n\n" + resumeText;
		String user = """
				Vaga: %s, em %s
				Senioridade: %s · Modelo de trabalho: %s
				Obrigatórias: %s
				Desejáveis: %s
				Resumo: %s

				Score determinístico: %d (%s)
				Skills atendidas: %s
				Lacunas: %s

				Exemplos recentes do feedback da pessoa:
				%s
				""".formatted(job.title(), job.company(), orDash(requirements.seniority()), requirements.workModel(),
				String.join(", ", requirements.requiredSkills()), String.join(", ", requirements.niceToHave()),
				orDash(requirements.summary()), base.score(), describe(base.breakdown()),
				String.join(", ", base.matchedSkills()), String.join(", ", base.gaps()), describeFeedback(feedback));
		return this.llm.structured(new LlmRequest("match-score", this.ai.models().scoring(), system, user, 1500,
				Effort.LOW), ScoreAdjustment.class);
	}

	List<String> resumeSkills(ResumeProfile resume) {
		// As skills extraídas na subida do currículo mais as que o dicionário reconhece hoje no texto (inclusive as
		// aceitas na revisão depois da subida).
		List<String> skills = new java.util.ArrayList<>(resume.skills());
		skills.addAll(this.dictionary.detect(resume.text()));
		return this.dictionary.canonicalize(skills);
	}

	private JobRequirements withDictionarySpelling(JobRequirements requirements) {
		List<String> required = this.dictionary.canonicalize(requirements.requiredSkills());
		List<String> nice = this.dictionary.canonicalize(requirements.niceToHave())
			.stream()
			.filter(skill -> !required.contains(skill))
			.toList();
		return new JobRequirements(requirements.seniority(), required, nice, requirements.workModel(),
				requirements.summary(), requirements.employerKind(), requirements.clientSectors(),
				requirements.contractType());
	}

	private static Optional<Seniority> targetSeniority(ResumeProfile resume) {
		return Seniority.detect(resume.targetRole()).or(() -> Seniority.detect(resume.label()));
	}

	private static String describe(Map<String, Integer> breakdown) {
		return breakdown.entrySet().stream().map(e -> e.getKey() + " " + e.getValue()).collect(Collectors.joining(", "));
	}

	private static String describeFeedback(List<FeedbackExample> feedback) {
		if (feedback.isEmpty()) {
			return "(nenhum ainda)";
		}
		return feedback.stream()
			.map(example -> "- " + example.rating() + ": " + example.title() + " (" + example.company() + ")"
					+ (example.reasons().isEmpty() ? "" : " — motivos: " + String.join(", ", example.reasons())))
			.collect(Collectors.joining("\n"));
	}

	private static int clamp(int score) {
		return Math.max(0, Math.min(100, score));
	}

	private static String orDash(@Nullable String value) {
		return (value == null || value.isBlank()) ? "—" : value;
	}

	/** Resultado de uma análise, pronto para ser salvo em {@code job_analysis}. */
	public record Outcome(String status, JobRequirements requirements, @Nullable Integer deterministicScore,
			@Nullable Integer llmAdjustment, @Nullable Integer matchScore, List<String> matchedSkills, List<String> gaps,
			@Nullable String justification, String modelVersion, @Nullable String error) {
	}

	/** Requisitos lidos de uma análise anterior. */
	public record StoredRequirements(@Nullable String seniority, List<String> requiredSkills, List<String> niceToHave,
			String workModel, @Nullable String summary, String employerKind, List<String> clientSectors,
			String contractType, String modelVersion) {

		JobRequirements toRequirements() {
			return new JobRequirements(this.seniority, this.requiredSkills, this.niceToHave, this.workModel, this.summary,
					this.employerKind, this.clientSectors, this.contractType);
		}

	}

	public record FeedbackExample(String title, String company, String rating, List<String> reasons) {
	}

	public record Fit(int score, List<String> matchedSkills, List<String> gaps) {
	}

}
