package dev.laranja.jobhunter.analysis.internal;

import dev.laranja.jobhunter.resumes.ResumeCatalog;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Na subida, sugere anos de experiência e inglês a partir do currículo padrão quando ainda não há sugestão (o
 * currículo foi analisado antes dela existir). Só regras, sem custo de LLM. Idempotente.
 */
@Component
class ProfileSuggestionBackfill implements ApplicationRunner {

	private final ResumeCatalog resumes;

	private final ResumeSkillsAnalyzer analyzer;

	ProfileSuggestionBackfill(ResumeCatalog resumes, ResumeSkillsAnalyzer analyzer) {
		this.resumes = resumes;
		this.analyzer = analyzer;
	}

	@Override
	public void run(ApplicationArguments args) {
		this.resumes.defaultResume().ifPresent(this.analyzer::suggestWithoutLlm);
	}

}
