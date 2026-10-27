package dev.laranja.jobhunter.analysis.internal;

import dev.laranja.jobhunter.analysis.JobAnalyzer;
import dev.laranja.jobhunter.jobs.JobCollected;
import dev.laranja.jobhunter.jobs.JobDescriptionAdded;
import dev.laranja.jobhunter.profile.CandidateProfileChanged;
import dev.laranja.jobhunter.resumes.DefaultResumeChanged;
import dev.laranja.jobhunter.resumes.ResumeCatalog;
import dev.laranja.jobhunter.resumes.ResumeUploaded;
import dev.laranja.jobhunter.shared.ai.LlmBudget;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Reações da análise aos eventos dos outros módulos. Os listeners rodam depois do commit, em transação própria;
 * o registro de eventos do Spring Modulith guarda os que falharem para reprocessar.
 */
@Component
class AnalysisListeners {

	private static final Logger log = LoggerFactory.getLogger(AnalysisListeners.class);

	private final JobAnalyzer analyzer;

	private final ResumeSkillsAnalyzer resumeSkills;

	private final ResumeCatalog resumes;

	private final LlmBudget budget;

	AnalysisListeners(JobAnalyzer analyzer, ResumeSkillsAnalyzer resumeSkills, ResumeCatalog resumes,
			LlmBudget budget) {
		this.analyzer = analyzer;
		this.budget = budget;
		this.resumeSkills = resumeSkills;
		this.resumes = resumes;
	}

	@ApplicationModuleListener
	void on(JobCollected event) {
		this.analyzer.analyze(event.jobId());
	}

	/** A vaga do alerta ganhou a descrição: a extração por LLM, antes sem texto, agora tem o que ler. */
	@ApplicationModuleListener
	void on(JobDescriptionAdded event) {
		this.analyzer.analyze(event.jobId());
	}

	/** Currículo novo: extrai as skills; se ele já é o padrão (o primeiro enviado), repontua as vagas. */
	@ApplicationModuleListener
	void on(ResumeUploaded event) {
		this.resumeSkills.ensureSkills(event.resumeVersionId());
		this.resumes.find(event.resumeVersionId())
			.filter(ResumeCatalog.ResumeProfile::isDefault)
			.ifPresent(resume -> log.info("{} vagas repontuadas com o currículo {}.", this.analyzer.rescoreOpenJobs(),
					resume.label()));
	}

	@ApplicationModuleListener
	void on(DefaultResumeChanged event) {
		this.resumeSkills.ensureSkills(event.resumeVersionId());
		log.info("Currículo padrão trocado: {} vagas repontuadas.", this.analyzer.rescoreOpenJobs());
	}

	/** Os dados da triagem mudaram: reavalia os eliminatórios e o score das vagas abertas. */
	@ApplicationModuleListener
	void on(CandidateProfileChanged event) {
		log.info("Dados da triagem alterados: {} vagas repontuadas.", this.analyzer.rescoreOpenJobs());
	}

	/**
	 * Completa a etapa do LLM das análises pendentes ou que falharam, aos poucos. Com o orçamento do mês das duas
	 * tarefas esgotado, nem tenta.
	 */
	@Scheduled(fixedDelayString = "PT15M", initialDelayString = "PT1M")
	void retryPending() {
		if (!this.budget.allows("extract-requirements") && !this.budget.allows("match-score")) {
			return;
		}
		for (long jobId : this.analyzer.dueForRetry(20)) {
			try {
				this.analyzer.analyze(jobId);
			}
			catch (RuntimeException ex) {
				log.warn("Nova tentativa de análise da vaga {} falhou: {}", jobId, ex.getMessage());
			}
		}
	}

}
