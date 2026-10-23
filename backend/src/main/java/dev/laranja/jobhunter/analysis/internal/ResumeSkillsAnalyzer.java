package dev.laranja.jobhunter.analysis.internal;

import java.util.List;

import dev.laranja.jobhunter.analysis.internal.LlmOutputs.ResumeSkills;
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

/** Skills do currículo: pelo LLM quando configurado, senão (ou se ele falhar) pelo dicionário. */
@Component
public class ResumeSkillsAnalyzer {

	private static final Logger log = LoggerFactory.getLogger(ResumeSkillsAnalyzer.class);

	private final ResumeCatalog resumes;

	private final SkillDictionary dictionary;

	private final LlmClient llm;

	private final AiProperties ai;

	ResumeSkillsAnalyzer(ResumeCatalog resumes, SkillDictionary dictionary, LlmClient llm, AiProperties ai) {
		this.resumes = resumes;
		this.dictionary = dictionary;
		this.llm = llm;
		this.ai = ai;
	}

	/** Extrai e grava as skills se ainda não houver; devolve se o currículo tem skills ao final. */
	public boolean ensureSkills(long resumeId) {
		ResumeProfile resume = this.resumes.find(resumeId).orElse(null);
		if (resume == null || !resume.skills().isEmpty()) {
			return resume != null;
		}
		if (resume.text() == null) {
			return false;
		}
		List<String> skills = this.dictionary.detect(resume.text());
		if (this.llm.enabled()) {
			try {
				ResumeSkills extracted = this.llm.structured(new LlmRequest("resume-skills", this.ai.models().extraction(),
						Prompts.RESUME_SKILLS.text(), resume.text(), 2000, Effort.LOW), ResumeSkills.class);
				skills = this.dictionary.canonicalize(extracted.skills());
			}
			catch (LlmException ex) {
				log.warn("Skills do currículo {} pelo dicionário: o LLM falhou ({}).", resumeId, ex.getMessage());
			}
		}
		this.resumes.updateSkills(resumeId, skills);
		return true;
	}

}
