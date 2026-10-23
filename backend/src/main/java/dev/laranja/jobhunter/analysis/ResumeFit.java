package dev.laranja.jobhunter.analysis;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import dev.laranja.jobhunter.analysis.internal.AnalysisEngine;
import dev.laranja.jobhunter.analysis.internal.AnalysisEngine.StoredRequirements;
import dev.laranja.jobhunter.analysis.internal.SkillDictionary;
import dev.laranja.jobhunter.shared.TextNormalizer;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.json.JsonMapper;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Aderência de um texto de currículo qualquer a uma vaga já analisada: quais palavras-chave da vaga aparecem no
 * texto (como um ATS lê) e o score determinístico. Serve para comparar o currículo base com uma versão adaptada.
 */
@Service
@Transactional(readOnly = true)
public class ResumeFit {

	private final AnalysisEngine engine;

	private final SkillDictionary dictionary;

	private final JdbcClient jdbc;

	private final JsonMapper json;

	ResumeFit(AnalysisEngine engine, SkillDictionary dictionary, JdbcClient jdbc, JsonMapper json) {
		this.engine = engine;
		this.dictionary = dictionary;
		this.jdbc = jdbc;
		this.json = json;
	}

	/** A análise salva da vaga; vazio enquanto a vaga não foi analisada. */
	public Optional<JobAnalysisView> analysis(long jobId) {
		return this.jdbc.sql("""
				select seniority, required_skills::text as required, nice_to_have::text as nice, work_model, summary,
				       employer_kind, client_sectors::text as sectors, contract_type, model_version, match_score,
				       matched_skills::text as matched, gaps::text as gaps, justification
				from job_analysis where job_id = :jobId
				""")
			.param("jobId", jobId)
			.query((rs, row) -> new JobAnalysisView(rs.getString("seniority"), readList(rs.getString("required")),
					readList(rs.getString("nice")), rs.getString("work_model"), rs.getString("summary"),
					rs.getString("employer_kind"), readList(rs.getString("sectors")), rs.getString("contract_type"),
					rs.getString("model_version"), (Integer) rs.getObject("match_score"), readList(rs.getString("matched")),
					readList(rs.getString("gaps")), rs.getString("justification")))
			.optional();
	}

	/**
	 * Palavras-chave da vaga presentes no texto e o score determinístico com as skills que o texto mostra. Uma
	 * palavra-chave conta quando o dicionário a reconhece no texto ou quando ela aparece como palavra inteira.
	 */
	public TextFit evaluate(JobAnalysisView analysis, String text, @Nullable String targetRole) {
		List<String> keywords = analysis.keywords();
		Set<String> detected = this.dictionary.detect(text)
			.stream()
			.map(skill -> skill.toLowerCase(Locale.ROOT))
			.collect(Collectors.toSet());
		String normalized = " " + TextNormalizer.normalize(text) + " ";
		List<String> covered = new ArrayList<>();
		List<String> missing = new ArrayList<>();
		for (String keyword : keywords) {
			String term = TextNormalizer.normalize(keyword);
			boolean present = detected.contains(keyword.toLowerCase(Locale.ROOT))
					|| (!term.isEmpty() && normalized.contains(" " + term + " "));
			(present ? covered : missing).add(keyword);
		}
		Set<String> skills = new LinkedHashSet<>(this.dictionary.detect(text));
		skills.addAll(covered);
		AnalysisEngine.Fit fit = this.engine.fit(analysis.toStored(), List.copyOf(skills), targetRole);
		return new TextFit(fit.score(), keywords, covered, missing);
	}

	/** Skills do dicionário que aparecem no texto, com a grafia canônica. */
	public List<String> skillsIn(String text) {
		return this.dictionary.detect(text);
	}

	@SuppressWarnings("unchecked")
	private List<String> readList(String value) {
		return (value == null) ? List.of() : (List<String>) this.json.readValue(value, List.class);
	}

	/** Requisitos e score da análise salva de uma vaga. */
	public record JobAnalysisView(@Nullable String seniority, List<String> requiredSkills, List<String> niceToHave,
			@Nullable String workModel, @Nullable String summary, @Nullable String employerKind,
			List<String> clientSectors, @Nullable String contractType, String modelVersion, @Nullable Integer matchScore,
			List<String> matchedSkills, List<String> gaps, @Nullable String justification) {

		/** Obrigatórias e depois desejáveis, sem repetir. */
		public List<String> keywords() {
			Set<String> seen = new LinkedHashSet<>();
			List<String> result = new ArrayList<>();
			for (String skill : this.requiredSkills) {
				if (seen.add(skill.toLowerCase(Locale.ROOT))) {
					result.add(skill);
				}
			}
			for (String skill : this.niceToHave) {
				if (seen.add(skill.toLowerCase(Locale.ROOT))) {
					result.add(skill);
				}
			}
			return result;
		}

		StoredRequirements toStored() {
			return new StoredRequirements(this.seniority, this.requiredSkills, this.niceToHave,
					(this.workModel != null) ? this.workModel : "NAO_INFORMADO", this.summary,
					(this.employerKind != null) ? this.employerKind : "CONTRATANTE", this.clientSectors,
					(this.contractType != null) ? this.contractType : "NAO_INFORMADO", this.modelVersion);
		}

	}

	/**
	 * @param score score determinístico (0 a 100) com as skills que o texto mostra
	 * @param keywords palavras-chave da vaga, obrigatórias primeiro
	 * @param covered palavras-chave que aparecem no texto
	 * @param missing palavras-chave que não aparecem
	 */
	public record TextFit(int score, List<String> keywords, List<String> covered, List<String> missing) {
	}

}
