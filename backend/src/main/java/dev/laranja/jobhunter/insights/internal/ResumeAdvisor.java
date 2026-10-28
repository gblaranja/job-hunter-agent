package dev.laranja.jobhunter.insights.internal;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import dev.laranja.jobhunter.insights.InsightReport.BulletRewrite;
import dev.laranja.jobhunter.insights.InsightReport.Funnel;
import dev.laranja.jobhunter.insights.InsightReport.Gap;
import dev.laranja.jobhunter.insights.InsightReport.GapAdvice;
import dev.laranja.jobhunter.insights.InsightReport.GupyProfile;
import dev.laranja.jobhunter.insights.InsightReport.LinkedinProfile;
import dev.laranja.jobhunter.insights.InsightReport.Recommendations;
import dev.laranja.jobhunter.insights.InsightReport.ResumePerformance;
import dev.laranja.jobhunter.insights.InsightReport.SkillDemand;
import dev.laranja.jobhunter.resumes.ResumeCatalog.ResumeProfile;
import dev.laranja.jobhunter.shared.ai.AiProperties;
import dev.laranja.jobhunter.shared.ai.LlmClient;
import dev.laranja.jobhunter.shared.ai.LlmClient.Effort;
import dev.laranja.jobhunter.shared.ai.LlmClient.LlmRequest;
import dev.laranja.jobhunter.shared.ai.LlmException;
import org.jspecify.annotations.Nullable;
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

	static final String PROMPT_ID = "resume-advice@v2";

	private static final Logger log = LoggerFactory.getLogger(ResumeAdvisor.class);

	private static final String PROMPT = load("prompts/resume-advice.v2.md");

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
				Recommendations deterministic = deterministic(facts, resume);
				Recommendations recommendations = new Recommendations(advice.gaps(), advice.bullets(),
						advice.linkedinKeywords(), advice.diagnosis(), advice.diagnosisExplanation(), "IA",
						(advice.gupyProfile() != null) ? advice.gupyProfile() : deterministic.gupyProfile(),
						(advice.linkedinProfile() != null && advice.linkedinProfile().headline() != null)
								? advice.linkedinProfile() : deterministic.linkedinProfile(),
						null);
				return new Advice(withChanges(recommendations), this.ai.models().insights() + "|" + PROMPT_ID);
			}
			catch (LlmException ex) {
				log.warn("Sugestões do LLM indisponíveis; usando as regras: {}", ex.getMessage());
			}
		}
		return new Advice(withChanges(deterministic(facts, resume)), "regras@v2");
	}

	/** Compara os textos dos perfis com os do relatório anterior. */
	private Recommendations withChanges(Recommendations current) {
		Recommendations previous = this.jdbc.sql("""
				select recommendations::text from insight_report where recommendations is not null
				order by created_at desc, id desc limit 1
				""")
			.query(String.class)
			.optional()
			.map(value -> this.json.readValue(value, Recommendations.class))
			.orElse(null);
		return current.withProfileChanges(profileChanges(previous, current));
	}

	static List<String> profileChanges(@Nullable Recommendations previous, Recommendations current) {
		List<String> changes = new ArrayList<>();
		LinkedinProfile before = (previous != null) ? previous.linkedinProfile() : null;
		LinkedinProfile after = current.linkedinProfile();
		if (after == null) {
			return changes;
		}
		if (before == null) {
			changes.add("Primeira versão dos textos dos perfis.");
			return changes;
		}
		if (!Objects.equals(before.headline(), after.headline())) {
			changes.add("Headline do LinkedIn mudou.");
		}
		if (!Objects.equals(before.about(), after.about())) {
			changes.add("Seção Sobre do LinkedIn mudou.");
		}
		Set<String> old = new LinkedHashSet<>(before.skills());
		List<String> added = after.skills().stream().filter(skill -> !old.contains(skill)).toList();
		if (!added.isEmpty()) {
			changes.add("Competências novas: " + String.join(", ", added) + ".");
		}
		GupyProfile gupyBefore = previous.gupyProfile();
		GupyProfile gupyAfter = current.gupyProfile();
		if (gupyAfter != null && (gupyBefore == null || !Objects.equals(gupyBefore.skills(), gupyAfter.skills()))) {
			changes.add("Habilidades da Gupy mudaram.");
		}
		return changes;
	}

	static Recommendations deterministic(Facts facts) {
		return deterministic(facts, null);
	}

	/**
	 * Lacunas mais frequentes como sugestões e o diagnóstico pelos números do funil. O headline sai do cargo-alvo
	 * mais as 5 skills cobertas mais pedidas.
	 */
	static Recommendations deterministic(Facts facts, @Nullable ResumeProfile resume) {
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
		List<String> top = keywords.stream().limit(5).toList();
		String role = (resume != null && resume.targetRole() != null && !resume.targetRole().isBlank())
				? resume.targetRole().strip() : null;
		LinkedinProfile linkedin = (role == null && top.isEmpty()) ? null
				: new LinkedinProfile(String.join(" | ", concat(role, top)), null, keywords);
		GupyProfile gupy = keywords.isEmpty() ? null : new GupyProfile(String.join(", ", keywords), List.of());
		return new Recommendations(gaps, List.of(), keywords, diagnosis, explanation, "REGRAS", gupy, linkedin, null);
	}

	private static List<String> concat(@Nullable String first, List<String> rest) {
		List<String> all = new ArrayList<>();
		if (first != null) {
			all.add(first);
		}
		all.addAll(rest);
		return all;
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
			String diagnosisExplanation, @Nullable GupyProfile gupyProfile, @Nullable LinkedinProfile linkedinProfile) {
	}

}
