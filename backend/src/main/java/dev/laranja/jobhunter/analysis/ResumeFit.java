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
				       matched_skills::text as matched, gaps::text as gaps, justification, knockouts::text as knockouts,
				       salary::text as salary, partial_skills::text as partial, job_family, international_eligibility,
				       evidence::text as evidence
				from job_analysis where job_id = :jobId
				""")
			.param("jobId", jobId)
			.query((rs, row) -> new JobAnalysisView(rs.getString("seniority"), readList(rs.getString("required")),
					readList(rs.getString("nice")), rs.getString("work_model"), rs.getString("summary"),
					rs.getString("employer_kind"), readList(rs.getString("sectors")), rs.getString("contract_type"),
					rs.getString("model_version"), (Integer) rs.getObject("match_score"), readList(rs.getString("matched")),
					readList(rs.getString("gaps")), rs.getString("justification"),
					readObjects(rs.getString("knockouts"), JobKnockout.class),
					(rs.getString("salary") != null) ? this.json.readValue(rs.getString("salary"), JobSalary.class) : null,
					readObjects(rs.getString("partial"), PartialSkillMatch.class), rs.getString("job_family"),
					rs.getString("international_eligibility"), readObjects(rs.getString("evidence"), RequirementEvidence.class)))
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

	/**
	 * Cobertura estilo Jobscan do texto para a vaga: as obrigatórias pesam o dobro das desejáveis, o título da vaga
	 * deve aparecer no headline, e o texto não deve repetir uma palavra-chave mais de {@value #MAX_REPEATS} vezes
	 * nem usar clichês.
	 */
	public AtsCoverage coverage(JobAnalysisView analysis, String jobTitle, String text, @Nullable String headline) {
		TextFit fit = evaluate(analysis, text, null);
		Set<String> covered = fit.covered().stream().map(k -> k.toLowerCase(Locale.ROOT)).collect(Collectors.toSet());
		Set<String> required = analysis.requiredSkills().stream().map(k -> k.toLowerCase(Locale.ROOT))
			.collect(Collectors.toSet());
		int total = 0;
		int got = 0;
		for (String keyword : fit.keywords()) {
			int weight = required.contains(keyword.toLowerCase(Locale.ROOT)) ? 2 : 1;
			total += weight;
			if (covered.contains(keyword.toLowerCase(Locale.ROOT))) {
				got += weight;
			}
		}
		Integer weighted = (total == 0) ? null : (int) Math.round(100.0 * got / total);
		String normalizedText = " " + TextNormalizer.normalize(text) + " ";
		List<String> overused = fit.keywords().stream().filter(keyword -> {
			String term = TextNormalizer.normalize(keyword);
			return !term.isEmpty() && occurrences(normalizedText, " " + term + " ") > MAX_REPEATS;
		}).toList();
		List<String> cliches = CLICHES.stream()
			.filter(cliche -> normalizedText.contains(" " + TextNormalizer.normalize(cliche) + " "))
			.toList();
		String title = TextNormalizer.normalize(jobTitle)
			.replaceAll("(^| )(senior|sr|pleno|pl|junior|jr|estagio|estagiario|i|ii|iii)(?= |$)", " ")
			.replaceAll("\\s+", " ")
			.strip();
		String head = TextNormalizer.normalize((headline != null) ? headline : firstLine(text));
		boolean titleInHeadline = !title.isEmpty() && head.contains(title);
		return new AtsCoverage(weighted, titleInHeadline, overused, cliches);
	}

	private static final int MAX_REPEATS = 4;

	private static final List<String> CLICHES = List.of("proativo", "proativa", "apaixonado por tecnologia",
			"apaixonada por tecnologia", "trabalho em equipe", "fora da caixa", "orientado a resultados",
			"orientada a resultados", "sinergia", "team player", "hard working", "results driven", "dinamico", "dinamica",
			"resiliente", "perfeccionista");

	private static int occurrences(String text, String needle) {
		int count = 0;
		for (int index = text.indexOf(needle); index >= 0; index = text.indexOf(needle, index + 1)) {
			count++;
		}
		return count;
	}

	private static String firstLine(String text) {
		return text.lines().map(String::strip).filter(line -> !line.isEmpty()).skip(1).findFirst().orElse("");
	}

	/** Skills do dicionário que aparecem no texto, com a grafia canônica. */
	public List<String> skillsIn(String text) {
		return this.dictionary.detect(text);
	}

	private <T> List<T> readObjects(String value, Class<T> type) {
		return (value == null) ? List.of()
				: this.json.readValue(value, this.json.getTypeFactory().constructCollectionType(List.class, type));
	}

	@SuppressWarnings("unchecked")
	private List<String> readList(String value) {
		return (value == null) ? List.of() : (List<String>) this.json.readValue(value, List.class);
	}

	/**
	 * Requisitos e score da análise salva de uma vaga.
	 *
	 * @param knockouts eliminatórios comparados com os dados da triagem
	 * @param salary remuneração informada na vaga
	 * @param partialSkills requisitos cobertos em parte por uma skill equivalente
	 * @param evidence trechos do currículo que comprovam requisitos (validados contra o texto)
	 */
	public record JobAnalysisView(@Nullable String seniority, List<String> requiredSkills, List<String> niceToHave,
			@Nullable String workModel, @Nullable String summary, @Nullable String employerKind,
			List<String> clientSectors, @Nullable String contractType, String modelVersion, @Nullable Integer matchScore,
			List<String> matchedSkills, List<String> gaps, @Nullable String justification, List<JobKnockout> knockouts,
			@Nullable JobSalary salary, List<PartialSkillMatch> partialSkills, @Nullable String jobFamily,
			@Nullable String internationalEligibility, List<RequirementEvidence> evidence) {

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
					(this.contractType != null) ? this.contractType : "NAO_INFORMADO", this.modelVersion, this.knockouts,
					this.salary, this.jobFamily, this.internationalEligibility);
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

	/**
	 * @param weightedCoverage palavras-chave cobertas, com as obrigatórias pesando o dobro (0 a 100); nula sem
	 * palavras-chave
	 * @param titleInHeadline o título da vaga (sem a senioridade) aparece no headline
	 * @param overusedKeywords palavras-chave repetidas mais de 4 vezes
	 * @param cliches clichês encontrados no texto
	 */
	public record AtsCoverage(@Nullable Integer weightedCoverage, boolean titleInHeadline, List<String> overusedKeywords,
			List<String> cliches) {
	}

}
