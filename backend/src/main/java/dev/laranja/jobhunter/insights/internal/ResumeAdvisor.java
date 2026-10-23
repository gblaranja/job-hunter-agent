package dev.laranja.jobhunter.insights.internal;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;

import dev.laranja.jobhunter.insights.InsightReport.BulletRewrite;
import dev.laranja.jobhunter.insights.InsightReport.Funnel;
import dev.laranja.jobhunter.insights.InsightReport.Gap;
import dev.laranja.jobhunter.insights.InsightReport.GapAdvice;
import dev.laranja.jobhunter.insights.InsightReport.Recommendations;
import dev.laranja.jobhunter.insights.InsightReport.ResumePerformance;
import dev.laranja.jobhunter.insights.InsightReport.SkillDemand;
import dev.laranja.jobhunter.resumes.ResumeCatalog.ResumeProfile;
import dev.laranja.jobhunter.shared.ai.AiProperties;
import dev.laranja.jobhunter.shared.ai.LlmClient;
import dev.laranja.jobhunter.shared.ai.LlmClient.Effort;
import dev.laranja.jobhunter.shared.ai.LlmClient.LlmRequest;
import dev.laranja.jobhunter.shared.ai.LlmException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.json.JsonMapper;

import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * Sugestões para o currículo. Com a Claude API, o modelo maior lê o currículo e os números da semana; sem ela (ou
 * se a chamada falhar), as regras apontam as lacunas mais frequentes e o diagnóstico pelo funil.
 */
@Component
class ResumeAdvisor {

	static final String PROMPT_ID = "resume-advice@v1";

	private static final Logger log = LoggerFactory.getLogger(ResumeAdvisor.class);

	private static final String PROMPT = load("prompts/resume-advice.v1.md");

	private final LlmClient llm;

	private final AiProperties ai;

	private final JdbcClient jdbc;

	private final JsonMapper json;

	ResumeAdvisor(LlmClient llm, AiProperties ai, JdbcClient jdbc, JsonMapper json) {
		this.llm = llm;
		this.ai = ai;
		this.jdbc = jdbc;
		this.json = json;
	}

	Advice advise(ResumeProfile resume, Facts facts) {
		if (this.llm.enabled() && resume.text() != null && !resume.text().isBlank()) {
			try {
				LlmAdvice advice = this.llm.structured(new LlmRequest("resume-advice", this.ai.models().insights(), PROMPT,
						userMessage(resume, facts), 16000, Effort.HIGH, true), LlmAdvice.class);
				return new Advice(new Recommendations(advice.gaps(), advice.bullets(), advice.linkedinKeywords(),
						advice.diagnosis(), advice.diagnosisExplanation(), "IA"), this.ai.models().insights() + "|" + PROMPT_ID);
			}
			catch (LlmException ex) {
				log.warn("Sugestões do LLM indisponíveis; usando as regras: {}", ex.getMessage());
			}
		}
		return new Advice(deterministic(facts), "regras@v1");
	}

	/** Lacunas mais frequentes como sugestões e o diagnóstico pelos números do funil. */
	static Recommendations deterministic(Facts facts) {
		List<GapAdvice> gaps = facts.gaps()
			.stream()
			.limit(5)
			.map(gap -> new GapAdvice(gap.skill(), gap.jobs() + " vagas recentes pedem " + gap.skill() + ".",
					"Se você já usou " + gap.skill() + ", deixe explícito no currículo (projeto, contexto e resultado). "
							+ "Se não, avalie um estudo ou projeto pessoal curto: ela aparece com frequência."))
			.toList();
		List<String> keywords = facts.topSkills().stream().filter(skill -> Boolean.TRUE.equals(skill.covered())).map(SkillDemand::skill).limit(10).toList();
		Funnel funnel = facts.funnel();
		String diagnosis;
		String explanation;
		double conversion = (funnel.applied() == 0) ? 0 : (double) funnel.interview() / funnel.applied();
		Integer avgScore = funnel.avgScoreApplied();
		if (funnel.applied() < 5) {
			diagnosis = "POUCOS_DADOS";
			explanation = "Só " + funnel.applied() + " candidaturas até agora; com 5 ou mais dá para ver onde o funil trava.";
		}
		else if (conversion >= 0.15) {
			diagnosis = "EQUILIBRADO";
			explanation = "%d de %d candidaturas chegaram à entrevista (%.0f%%).".formatted(funnel.interview(),
					funnel.applied(), conversion * 100);
		}
		else if (avgScore != null && avgScore < 60) {
			diagnosis = (avgScore < 45) ? "FILTRO" : "AMBOS";
			explanation = "O score médio das vagas em que você se candidatou é %d e só %.0f%% viraram entrevista: priorize vagas com score acima de 70."
				.formatted(avgScore, conversion * 100);
		}
		else {
			diagnosis = "CURRICULO";
			explanation = "As vagas candidatadas são aderentes (score médio %s), mas só %.0f%% viraram entrevista: o currículo não está mostrando a aderência."
				.formatted((avgScore != null) ? avgScore : "—", conversion * 100);
		}
		return new Recommendations(gaps, List.of(), keywords, diagnosis, explanation, "REGRAS");
	}

	private String userMessage(ResumeProfile resume, Facts facts) {
		record Sample(String title, String company, Integer score, List<String> gaps) {
		}
		List<Sample> samples = this.jdbc.sql("""
				select j.title, j.company, a.match_score, a.gaps::text as gaps
				from job j join job_analysis a on a.job_id = j.id
				where j.closed_at is null and a.match_score is not null
				order by j.first_seen_at desc, a.match_score desc limit 15
				""")
			.query((rs, row) -> new Sample(rs.getString("title"), rs.getString("company"), rs.getInt("match_score"),
					List.of(this.json.readValue(rs.getString("gaps"), String[].class))))
			.list();
		return """
				Currículo atual (%s):
				%s

				Números das últimas 4 semanas:
				%s

				Vagas recentes com score e lacunas:
				%s
				""".formatted(resume.label(), resume.text(), this.json.writeValueAsString(facts),
				this.json.writeValueAsString(samples));
	}

	private static String load(String path) {
		try {
			return new ClassPathResource(path).getContentAsString(StandardCharsets.UTF_8).strip();
		}
		catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
	}

	/** O que o relatório já calculou e vai para o LLM. */
	record Facts(List<SkillDemand> topSkills, List<Gap> gaps, Funnel funnel, List<ResumePerformance> resumePerformance,
			Set<String> resumeSkills) {
	}

	record Advice(Recommendations recommendations, String modelVersion) {
	}

	/** Saída estruturada pedida ao modelo. */
	record LlmAdvice(List<GapAdvice> gaps, List<BulletRewrite> bullets, List<String> linkedinKeywords, String diagnosis,
			String diagnosisExplanation) {
	}

}
