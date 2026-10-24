package dev.laranja.jobhunter.insights.internal;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

import dev.laranja.jobhunter.insights.FunnelBreakdown;
import dev.laranja.jobhunter.insights.InsightReport.Funnel;
import dev.laranja.jobhunter.insights.InsightReport.Gap;
import dev.laranja.jobhunter.insights.InsightReport.Recommendations;
import dev.laranja.jobhunter.insights.InsightReport.ResumePerformance;
import dev.laranja.jobhunter.insights.InsightReport.SkillDemand;
import dev.laranja.jobhunter.insights.InsightReport.SuggestedFilter;
import dev.laranja.jobhunter.insights.LlmUsage;
import dev.laranja.jobhunter.resumes.ResumeCatalog;
import dev.laranja.jobhunter.resumes.ResumeCatalog.ResumeProfile;
import org.jspecify.annotations.Nullable;

import org.springframework.stereotype.Component;

/** Junta as agregações e as sugestões num rascunho de relatório. */
@Component
public class InsightEngine {

	private final InsightAggregator aggregator;

	private final ResumeAdvisor advisor;

	private final ResumeCatalog resumes;

	InsightEngine(InsightAggregator aggregator, ResumeAdvisor advisor, ResumeCatalog resumes) {
		this.aggregator = aggregator;
		this.advisor = advisor;
		this.resumes = resumes;
	}

	/**
	 * @param since início do período das vagas e dos feedbacks
	 * @param accuracySince início da janela da taxa de acerto da IA
	 * @param monthStart início do mês, para o custo da Claude API
	 */
	public Draft compute(Instant since, Instant accuracySince, LocalDate monthStart) {
		ResumeProfile resume = this.resumes.defaultResume()
			.orElse(new ResumeProfile(0, "sem currículo", null, null, List.of(), true));
		Set<String> skills = resume.skills().stream().map(s -> s.toLowerCase(Locale.ROOT)).collect(Collectors.toSet());
		List<SkillDemand> topSkills = this.aggregator.topSkills(since, skills);
		List<Gap> gaps = this.aggregator.gaps(since);
		Funnel funnel = this.aggregator.funnel();
		List<ResumePerformance> performance = this.aggregator.resumePerformance();
		List<SuggestedFilter> filters = this.aggregator.suggestedFilters(since, skills);
		BigDecimal accuracy = this.aggregator.aiAccuracy(accuracySince);
		ResumeAdvisor.Advice advice = this.advisor
			.advise(resume, new ResumeAdvisor.Facts(topSkills, gaps, funnel, performance, skills));
		// O custo é lido depois das sugestões, para incluir a própria chamada do relatório.
		BigDecimal cost = this.aggregator.usage(monthStart)
			.stream()
			.map(LlmUsage::costUsd)
			.reduce(BigDecimal.ZERO, BigDecimal::add);
		return new Draft(topSkills, gaps, funnel, performance, filters, accuracy, cost, advice.recommendations(),
				advice.modelVersion());
	}

	public FunnelBreakdown breakdown() {
		return this.aggregator.breakdown();
	}

	public List<LlmUsage> usage(LocalDate monthStart) {
		return this.aggregator.usage(monthStart);
	}

	public record Draft(List<SkillDemand> topSkills, List<Gap> gaps, Funnel funnel,
			List<ResumePerformance> resumePerformance, List<SuggestedFilter> suggestedFilters,
			@Nullable BigDecimal aiAccuracy, BigDecimal llmCostUsd, Recommendations recommendations,
			String modelVersion) {
	}

}
