package dev.laranja.jobhunter.analysis.internal;

import java.time.Clock;
import java.time.Year;
import java.util.List;
import java.util.Optional;

import dev.laranja.jobhunter.analysis.internal.LlmOutputs.ResumeSkills;
import dev.laranja.jobhunter.profile.CandidateProfiles;
import dev.laranja.jobhunter.profile.EnglishLevel;
import dev.laranja.jobhunter.resumes.ResumeCatalog;
import dev.laranja.jobhunter.resumes.ResumeCatalog.ResumeProfile;
import dev.laranja.jobhunter.shared.ai.AiProperties;
import dev.laranja.jobhunter.shared.ai.LlmClient;
import dev.laranja.jobhunter.shared.ai.LlmClient.Effort;
import dev.laranja.jobhunter.shared.ai.LlmClient.LlmRequest;
import dev.laranja.jobhunter.shared.ai.LlmException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.stereotype.Component;

/**
 * Skills do currículo: pelo LLM quando configurado, senão (ou se ele falhar) pelo dicionário. Na mesma leitura,
 * os anos de experiência e o nível de inglês viram sugestão para os dados da triagem.
 */
@Component
public class ResumeSkillsAnalyzer {

	private static final Logger log = LoggerFactory.getLogger(ResumeSkillsAnalyzer.class);

	private final ResumeCatalog resumes;

	private final SkillDictionary dictionary;

	private final LlmClient llm;

	private final AiProperties ai;

	private final CandidateProfiles profiles;

	private final Clock clock;

	ResumeSkillsAnalyzer(ResumeCatalog resumes, SkillDictionary dictionary, LlmClient llm, AiProperties ai,
			CandidateProfiles profiles, Clock clock) {
		this.resumes = resumes;
		this.dictionary = dictionary;
		this.llm = llm;
		this.ai = ai;
		this.profiles = profiles;
		this.clock = clock;
	}

	/** Extrai e grava as skills se ainda não houver; devolve se o currículo tem skills ao final. */
	public boolean ensureSkills(long resumeId) {
		ResumeProfile resume = this.resumes.find(resumeId).orElse(null);
		if (resume == null) {
			return false;
		}
		if (!resume.skills().isEmpty()) {
			if (resume.isDefault()) {
				suggestWithoutLlm(resume);
			}
			return true;
		}
		if (resume.text() == null) {
			return false;
		}
		List<String> skills = this.dictionary.detect(resume.text());
		Optional<Integer> years = ResumeHints.yearsOfExperience(resume.text(), Year.now(this.clock));
		Optional<EnglishLevel> english = ResumeHints.englishLevel(resume.text());
		if (this.llm.enabled()) {
			try {
				ResumeSkills extracted = this.llm.structured(new LlmRequest("resume-skills", this.ai.models().extraction(),
						Prompts.RESUME_SKILLS.text(), resume.text(), 2000, Effort.LOW), ResumeSkills.class);
				skills = this.dictionary.canonicalize(extracted.skills());
				years = Optional.ofNullable(extracted.yearsOfExperience()).filter(value -> value >= 0 && value <= 60)
					.or(() -> ResumeHints.yearsOfExperience(resume.text(), Year.now(this.clock)));
				english = ResumeHints.parseEnglish(extracted.englishLevel()).or(() -> ResumeHints.englishLevel(resume.text()));
			}
			catch (LlmException ex) {
				log.warn("Skills do currículo {} pelo dicionário: o LLM falhou ({}).", resumeId, ex.getMessage());
			}
		}
		this.resumes.updateSkills(resumeId, skills);
		this.profiles.suggest(years.orElse(null), english.orElse(null), resume.label());
		return true;
	}

	/**
	 * Sugestão pelas regras, para o currículo padrão cujas skills foram lidas antes da sugestão existir. Não
	 * sobrescreve uma sugestão já feita.
	 */
	public void suggestWithoutLlm(ResumeProfile resume) {
		if (resume.text() == null || this.profiles.current().suggestedFrom() != null) {
			return;
		}
		this.profiles.suggest(ResumeHints.yearsOfExperience(resume.text(), Year.now(this.clock)).orElse(null),
				ResumeHints.englishLevel(resume.text()).orElse(null), resume.label());
	}

}
