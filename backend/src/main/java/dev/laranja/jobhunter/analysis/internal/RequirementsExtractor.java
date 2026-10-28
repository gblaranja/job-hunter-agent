package dev.laranja.jobhunter.analysis.internal;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import dev.laranja.jobhunter.analysis.JobSalary;
import dev.laranja.jobhunter.analysis.Seniority;
import dev.laranja.jobhunter.jobs.JobView;
import dev.laranja.jobhunter.shared.TextNormalizer;
import dev.laranja.jobhunter.shared.ai.AiProperties;
import dev.laranja.jobhunter.shared.ai.LlmClient;
import dev.laranja.jobhunter.shared.ai.LlmClient.Effort;
import dev.laranja.jobhunter.shared.ai.LlmClient.LlmRequest;

import org.springframework.stereotype.Component;

/** Extrai os requisitos da vaga: pelo dicionário e por regras, e, com o LLM, pela leitura da descrição. */
@Component
class RequirementsExtractor {

	/** A partir deste ponto da descrição, as skills contam como diferenciais. */
	private static final Pattern NICE_TO_HAVE_SECTION = Pattern
		.compile("(diferencia(l|is)|desejave(l|is)|seria um plus|sera um plus|nice to have|bonus|plus)");

	private static final Pattern PJ = Pattern.compile("(^| )pj( |$)|pessoa juridica");

	private static final Pattern CLT = Pattern.compile("(^| )clt( |$)");

	private static final Pattern CONSULTANCY = Pattern.compile("alocad[oa]|alocacao|atuar no cliente|projeto (para|no) cliente|nosso cliente");

	private final SkillDictionary dictionary;

	private final LlmClient llm;

	private final AiProperties ai;

	RequirementsExtractor(SkillDictionary dictionary, LlmClient llm, AiProperties ai) {
		this.dictionary = dictionary;
		this.llm = llm;
		this.ai = ai;
	}

	/** Sem LLM: skills do dicionário (as que aparecem depois de "diferenciais" viram desejáveis) e regras simples. */
	JobRequirements deterministic(JobView job) {
		String description = (job.description() == null) ? "" : job.description();
		String normalized = TextNormalizer.normalize(description);
		Matcher niceSection = NICE_TO_HAVE_SECTION.matcher(normalized);
		List<String> required = new ArrayList<>(this.dictionary.detect(job.title()));
		List<String> nice = new ArrayList<>();
		if (niceSection.find()) {
			addMissing(required, this.dictionary.detect(normalized.substring(0, niceSection.start())));
			this.dictionary.detect(normalized.substring(niceSection.start()))
				.stream()
				.filter(skill -> !required.contains(skill))
				.forEach(nice::add);
		}
		else {
			addMissing(required, this.dictionary.detect(normalized));
		}
		String seniority = Seniority.detect(job.title())
			.or(() -> Seniority.detect(normalized.substring(0, Math.min(400, normalized.length()))))
			.map(Enum::name)
			.orElse(null);
		boolean pj = PJ.matcher(normalized).find();
		boolean clt = CLT.matcher(normalized).find();
		String contract = (pj && clt) ? "AMBOS" : pj ? "PJ" : clt ? "CLT" : "NAO_INFORMADO";
		String employer = CONSULTANCY.matcher(normalized).find() ? "CONSULTORIA_TI" : "CONTRATANTE";
		return new JobRequirements(seniority, required, nice, job.workModel().name(), null, employer, List.of(), contract,
				KnockoutRules.extract(description, contract, job.workModel().name(), job.location()),
				KnockoutRules.salary(description, contract), null, null);
	}

	/** Requisitos salvos completados pelas regras: eliminatórios dos tipos que faltam e o salário. */
	JobRequirements withRules(JobRequirements stored, JobView job) {
		List<KnockoutRequirement> rules = KnockoutRules.extract(job.description(), stored.contractType(),
				stored.workModel(), job.location());
		JobSalary salary = (stored.salary() != null) ? stored.salary()
				: KnockoutRules.salary(job.description(), stored.contractType());
		return new JobRequirements(stored.seniority(), stored.requiredSkills(), stored.niceToHave(), stored.workModel(),
				stored.summary(), stored.employerKind(), stored.clientSectors(), stored.contractType(),
				mergeKnockouts(stored.knockouts(), rules), salary, stored.jobFamily(), stored.internationalEligibility());
	}

	boolean canUseLlm(JobView job) {
		return this.llm.enabled() && job.description() != null && job.description().length() >= 80;
	}

	/** Com o LLM: leitura da descrição completa, com as skills normalizadas pelo dicionário. */
	JobRequirements withLlm(JobView job) {
		String user = """
				Título: %s
				Empresa: %s
				Localidade: %s
				Modelo de trabalho informado: %s

				Descrição:
				%s
				""".formatted(job.title(), job.company(), nullToDash(job.location()), job.workModel(), job.description());
		JobRequirements extracted = this.llm.structured(new LlmRequest("extract-requirements", this.ai.models().extraction(),
				Prompts.EXTRACT_REQUIREMENTS.text(), user, 4000, Effort.LOW), JobRequirements.class);
		List<String> required = this.dictionary.canonicalize(orEmpty(extracted.requiredSkills()));
		List<String> nice = this.dictionary.canonicalize(orEmpty(extracted.niceToHave()))
			.stream()
			.filter(skill -> !required.contains(skill))
			.toList();
		String seniority = Seniority.parse(extracted.seniority()).map(Enum::name).orElse(null);
		String workModel = orDefault(extracted.workModel(), job.workModel().name());
		String contract = orDefault(extracted.contractType(), "NAO_INFORMADO");
		List<KnockoutRequirement> rules = KnockoutRules.extract(job.description(), contract, workModel, job.location());
		JobSalary salary = (extracted.salary() != null && extracted.salary().isInformed()) ? extracted.salary()
				: KnockoutRules.salary(job.description(), contract);
		return new JobRequirements(seniority, required, nice, workModel, extracted.summary(),
				orDefault(extracted.employerKind(), "CONTRATANTE"), orEmpty(extracted.clientSectors()), contract,
				mergeKnockouts(extracted.knockouts(), rules), salary, blankToNull(extracted.jobFamily()),
				blankToNull(extracted.internationalEligibility()));
	}

	/** Os eliminatórios do LLM; as regras completam os tipos que ele não trouxe. */
	static List<KnockoutRequirement> mergeKnockouts(List<KnockoutRequirement> llm, List<KnockoutRequirement> rules) {
		List<KnockoutRequirement> merged = new ArrayList<>();
		java.util.Set<String> types = new java.util.HashSet<>();
		for (KnockoutRequirement knockout : (llm == null) ? List.<KnockoutRequirement>of() : llm) {
			if (knockout != null && KnockoutEvaluator.parseType(knockout.type()).isPresent() && knockout.value() != null
					&& !knockout.value().isBlank()) {
				merged.add(knockout);
				types.add(knockout.type().strip().toUpperCase(java.util.Locale.ROOT));
			}
		}
		rules.stream().filter(rule -> !types.contains(rule.type())).forEach(merged::add);
		return merged;
	}

	private static String blankToNull(String value) {
		return (value == null || value.isBlank()) ? null : value.strip().toUpperCase(java.util.Locale.ROOT);
	}

	String extractionVersion() {
		return this.ai.models().extraction() + "|" + Prompts.EXTRACT_REQUIREMENTS.id();
	}

	private static void addMissing(List<String> target, List<String> skills) {
		skills.stream().filter(skill -> !target.contains(skill)).forEach(target::add);
	}

	private static List<String> orEmpty(List<String> values) {
		return (values == null) ? List.of() : values;
	}

	private static String orDefault(String value, String fallback) {
		return (value == null || value.isBlank()) ? fallback : value;
	}

	private static String nullToDash(String value) {
		return (value == null) ? "—" : value;
	}

}
