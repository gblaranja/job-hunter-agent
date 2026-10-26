package dev.laranja.jobhunter.tailoring;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import dev.laranja.jobhunter.analysis.JobKnockout;
import dev.laranja.jobhunter.analysis.ResumeFit;
import dev.laranja.jobhunter.analysis.ResumeFit.JobAnalysisView;
import dev.laranja.jobhunter.jobs.JobCatalog;
import dev.laranja.jobhunter.jobs.JobView;
import dev.laranja.jobhunter.profile.CandidateProfileView;
import dev.laranja.jobhunter.profile.CandidateProfiles;
import dev.laranja.jobhunter.resumes.ResumeCatalog;
import dev.laranja.jobhunter.resumes.ResumeCatalog.ResumeProfile;
import dev.laranja.jobhunter.shared.BusinessRuleException;
import dev.laranja.jobhunter.shared.TextNormalizer;
import dev.laranja.jobhunter.shared.ai.AiProperties;
import dev.laranja.jobhunter.shared.ai.LlmClient;
import dev.laranja.jobhunter.shared.ai.LlmClient.Effort;
import dev.laranja.jobhunter.shared.ai.LlmClient.LlmRequest;
import dev.laranja.jobhunter.shared.ai.LlmException;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

/**
 * Rascunhos das respostas do formulário de candidatura (perguntas de triagem) e da carta curta, a partir do
 * currículo padrão, da análise da vaga com as evidências e dos dados da triagem. Nada é enviado: a pessoa revisa e
 * cola. Sem a Claude API, as perguntas sobre pretensão, anos de experiência e inglês saem dos dados informados e as
 * demais ficam para a pessoa responder.
 */
@Service
public class ScreeningAnswers {

	static final String PROMPT_ID = "screening-answers@v1";

	private static final Logger log = LoggerFactory.getLogger(ScreeningAnswers.class);

	private static final String PROMPT = load("prompts/screening-answers.v1.md");

	private static final int RESUME_CHARACTERS = 20_000;

	private final JobCatalog jobs;

	private final ResumeCatalog resumes;

	private final ResumeFit fit;

	private final CandidateProfiles profiles;

	private final LlmClient llm;

	private final AiProperties ai;

	ScreeningAnswers(JobCatalog jobs, ResumeCatalog resumes, ResumeFit fit, CandidateProfiles profiles, LlmClient llm,
			AiProperties ai) {
		this.jobs = jobs;
		this.resumes = resumes;
		this.fit = fit;
		this.profiles = profiles;
		this.llm = llm;
		this.ai = ai;
	}

	public ScreeningAnswersView answer(long jobId, List<String> questions, boolean coverLetter) {
		List<String> cleaned = questions.stream().filter(q -> q != null && !q.isBlank()).map(String::strip).toList();
		if (cleaned.isEmpty() && !coverLetter) {
			throw new BusinessRuleException("Informe ao menos uma pergunta ou peça a carta.");
		}
		JobView job = this.jobs.get(jobId);
		ResumeProfile resume = this.resumes.defaultResume()
			.orElseThrow(() -> new BusinessRuleException("Envie um currículo antes de pedir as respostas."));
		JobAnalysisView analysis = this.fit.analysis(jobId).orElse(null);
		CandidateProfileView profile = this.profiles.current();
		if (this.llm.enabled() && resume.text() != null && !resume.text().isBlank()) {
			try {
				ScreeningDraft draft = this.llm.structured(new LlmRequest("screening-answers", this.ai.models().drafting(),
						PROMPT + "\n\n" + truncate(resume.text()), userMessage(job, analysis, profile, cleaned, coverLetter),
						6000, Effort.MEDIUM), ScreeningDraft.class);
				return fromDraft(cleaned, draft, coverLetter);
			}
			catch (LlmException ex) {
				log.warn("Respostas de triagem pelas regras: o LLM falhou ({}).", ex.getMessage());
			}
		}
		return deterministic(job, analysis, profile, resume, cleaned, coverLetter);
	}

	private ScreeningAnswersView fromDraft(List<String> questions, ScreeningDraft draft, boolean coverLetter) {
		List<ScreeningAnswer> answers = new ArrayList<>();
		List<DraftAnswer> drafted = (draft.answers() == null) ? List.of() : draft.answers();
		for (int i = 0; i < questions.size(); i++) {
			DraftAnswer item = (i < drafted.size()) ? drafted.get(i) : null;
			String text = (item == null || item.answer() == null) ? "" : item.answer().strip();
			boolean needsInput = item == null || item.needsInput() || text.isEmpty();
			answers.add(new ScreeningAnswer(questions.get(i), text, (item != null) ? item.basis() : null, needsInput));
		}
		String letter = (coverLetter && draft.coverLetter() != null && !draft.coverLetter().isBlank())
				? draft.coverLetter().strip() : null;
		return new ScreeningAnswersView(answers, letter, "IA");
	}

	/** Sem LLM: o que os dados da triagem respondem; o resto fica para a pessoa. */
	static ScreeningAnswersView deterministic(JobView job, @Nullable JobAnalysisView analysis,
			CandidateProfileView profile, ResumeProfile resume, List<String> questions, boolean coverLetter) {
		List<ScreeningAnswer> answers = questions.stream()
			.map(question -> ruleAnswer(question, profile, (analysis != null) ? analysis.contractType() : null))
			.toList();
		String letter = coverLetter ? letter(job, analysis, resume) : null;
		return new ScreeningAnswersView(answers, letter, "REGRAS");
	}

	private static ScreeningAnswer ruleAnswer(String question, CandidateProfileView profile, @Nullable String contract) {
		String q = " " + TextNormalizer.normalize(question) + " ";
		if (q.matches(".*( pretensao | salari| remuneracao | salary | compensation ).*")) {
			BigDecimal value = "PJ".equals(contract) && profile.desiredSalaryPj() != null ? profile.desiredSalaryPj()
					: (profile.desiredSalaryClt() != null) ? profile.desiredSalaryClt() : profile.desiredSalaryPj();
			if (value != null) {
				String regime = (value == profile.desiredSalaryPj()) ? "PJ" : "CLT";
				return new ScreeningAnswer(question, money(value) + " por mês (" + regime + ")", "dados da triagem", false);
			}
			return needs(question, "Informe sua pretensão em Configurações ou responda aqui.");
		}
		if (q.matches(".*( anos | years ).*") && q.matches(".*( experiencia | experience ).*")) {
			if (profile.yearsOfExperience() != null) {
				return new ScreeningAnswer(question, profile.yearsOfExperience() + " anos", "dados da triagem", false);
			}
			return needs(question, "Informe seus anos de experiência em Configurações.");
		}
		if (q.matches(".*( ingles | english ).*")) {
			if (profile.englishLevel() != null) {
				String level = profile.englishLevel().name().toLowerCase(Locale.ROOT);
				return new ScreeningAnswer(question, "Inglês " + level, "dados da triagem", false);
			}
			return needs(question, "Informe seu nível de inglês em Configurações.");
		}
		return needs(question, "Responda com base no seu currículo; sem a Claude API não há rascunho.");
	}

	private static ScreeningAnswer needs(String question, String basis) {
		return new ScreeningAnswer(question, "", basis, true);
	}

	private static String letter(JobView job, @Nullable JobAnalysisView analysis, ResumeProfile resume) {
		List<String> matched = (analysis != null) ? analysis.matchedSkills().stream().limit(4).toList() : List.of();
		String evidence = (analysis == null) ? ""
				: analysis.evidence().stream().limit(2).map(item -> "\"" + item.quote() + "\"").collect(Collectors.joining("; "));
		StringBuilder text = new StringBuilder();
		text.append("Olá! Tenho interesse na vaga de ").append(job.title()).append(" na ").append(job.company()).append('.');
		if (resume.targetRole() != null) {
			text.append(" Atuo como ").append(resume.targetRole()).append('.');
		}
		if (!matched.isEmpty()) {
			text.append("\n\nTrabalho com ").append(String.join(", ", matched)).append(", que a vaga pede.");
		}
		if (!evidence.isEmpty()) {
			text.append(" No meu currículo: ").append(evidence).append('.');
		}
		text.append("\n\nFico à disposição para conversar.");
		return text.toString();
	}

	private static String userMessage(JobView job, @Nullable JobAnalysisView analysis, CandidateProfileView profile,
			List<String> questions, boolean coverLetter) {
		String knockouts = (analysis == null) ? "—"
				: analysis.knockouts()
					.stream()
					.map(JobKnockout::reason)
					.collect(Collectors.joining("; "));
		String evidence = (analysis == null) ? "—"
				: analysis.evidence()
					.stream()
					.map(item -> item.requirement() + ": \"" + item.quote() + "\"")
					.collect(Collectors.joining("; "));
		StringBuilder numbered = new StringBuilder();
		for (int i = 0; i < questions.size(); i++) {
			numbered.append(i + 1).append(". ").append(questions.get(i)).append('\n');
		}
		return """
				Vaga: %s, em %s (%s)
				Obrigatórias: %s
				Desejáveis: %s
				Regime: %s
				Eliminatórios: %s
				Evidências no currículo: %s

				Dados da pessoa: anos de experiência %s; inglês %s; local %s; regimes aceitos %s; pretensão CLT %s; pretensão PJ %s

				Perguntas (responda na mesma ordem):
				%s
				Carta de apresentação: %s

				Descrição da vaga:
				%s
				""".formatted(job.title(), job.company(), orDash(job.location()),
				(analysis != null) ? String.join(", ", analysis.requiredSkills()) : "—",
				(analysis != null) ? String.join(", ", analysis.niceToHave()) : "—",
				(analysis != null) ? orDash(analysis.contractType()) : "—", knockouts, evidence,
				orDash(profile.yearsOfExperience()), orDash(profile.englishLevel()),
				orDash((profile.city() != null ? profile.city() : "") + (profile.state() != null ? "/" + profile.state() : "")),
				String.join(", ", profile.acceptedContracts()),
				(profile.desiredSalaryClt() != null) ? money(profile.desiredSalaryClt()) : "não informada",
				(profile.desiredSalaryPj() != null) ? money(profile.desiredSalaryPj()) : "não informada",
				questions.isEmpty() ? "(nenhuma)\n" : numbered, coverLetter ? "sim" : "não",
				(job.description() == null) ? "(sem descrição)" : job.description());
	}

	private static String money(BigDecimal value) {
		return NumberFormat.getCurrencyInstance(Locale.of("pt", "BR")).format(value).replace(' ', ' ');
	}

	private static String orDash(@Nullable Object value) {
		return (value == null || value.toString().isBlank()) ? "—" : value.toString();
	}

	private static String truncate(String text) {
		return text.substring(0, Math.min(RESUME_CHARACTERS, text.length()));
	}

	private static String load(String path) {
		try {
			return new ClassPathResource(path).getContentAsString(StandardCharsets.UTF_8).strip();
		}
		catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
	}

	/**
	 * Rascunhos para o formulário da vaga.
	 *
	 * @param source IA ou REGRAS
	 */
	public record ScreeningAnswersView(List<ScreeningAnswer> answers, @Nullable String coverLetter, String source) {
	}

	/**
	 * @param basis de onde veio a resposta, ou o que a pessoa precisa informar
	 * @param needsInput a resposta depende de algo que o currículo e os dados não mostram
	 */
	public record ScreeningAnswer(String question, String answer, @Nullable String basis, boolean needsInput) {
	}

	/** Saída estruturada pedida ao modelo. */
	record ScreeningDraft(@JsonPropertyDescription("Uma resposta por pergunta, na mesma ordem") List<DraftAnswer> answers,
			@JsonPropertyDescription("Carta curta, se pedida; nula se não") @Nullable String coverLetter) {
	}

	record DraftAnswer(@JsonPropertyDescription("Resposta em primeira pessoa; vazia se falta informação") String answer,
			@JsonPropertyDescription("De onde veio a resposta, ou o que a pessoa precisa informar") String basis,
			@JsonPropertyDescription("Verdadeiro se a resposta depende de algo que o currículo e os dados não mostram") boolean needsInput) {
	}

}
