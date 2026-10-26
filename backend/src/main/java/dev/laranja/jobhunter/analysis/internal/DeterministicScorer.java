package dev.laranja.jobhunter.analysis.internal;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import dev.laranja.jobhunter.analysis.PartialSkillMatch;
import dev.laranja.jobhunter.analysis.Seniority;

import org.springframework.stereotype.Component;

/**
 * Score determinístico de aderência (0 a 100): obrigatórias atendidas (70), desejáveis (15), senioridade (10) e
 * modelo de trabalho (5). É explicável e não oscila entre execuções; o LLM só ajusta em cima dele. As skills do
 * currículo contam com as que elas implicam (Spring Boot implica Java), e uma skill equivalente em parte
 * (RabbitMQ para Kafka) vale metade do requisito.
 */
@Component
class DeterministicScorer {

	static final int REQUIRED_WEIGHT = 70;

	static final int NICE_WEIGHT = 15;

	static final int SENIORITY_WEIGHT = 10;

	static final int WORK_MODEL_WEIGHT = 5;

	/** Peso de um requisito coberto por uma skill equivalente em parte. */
	static final double PARTIAL_CREDIT = 0.5;

	private final SkillDictionary dictionary;

	DeterministicScorer(SkillDictionary dictionary) {
		this.dictionary = dictionary;
	}

	Result score(JobRequirements requirements, List<String> resumeSkills, Optional<Seniority> targetSeniority) {
		Map<String, String> owned = new LinkedHashMap<>();
		this.dictionary.withImplied(resumeSkills).forEach(skill -> owned.putIfAbsent(skill.toLowerCase(Locale.ROOT), skill));
		List<String> matched = new ArrayList<>();
		List<String> gaps = new ArrayList<>();
		List<PartialSkillMatch> partial = new ArrayList<>();
		double requiredCredit = credit(requirements.requiredSkills(), owned, matched, partial, gaps);
		double requiredRatio = requirements.requiredSkills().isEmpty() ? 0.5
				: requiredCredit / requirements.requiredSkills().size();
		double niceCredit = credit(requirements.niceToHave(), owned, matched, partial, new ArrayList<>());
		// Sem desejáveis listados, vale a mesma proporção das obrigatórias.
		double niceRatio = requirements.niceToHave().isEmpty() ? requiredRatio
				: niceCredit / requirements.niceToHave().size();

		int required = (int) Math.round(REQUIRED_WEIGHT * requiredRatio);
		int nice = (int) Math.round(NICE_WEIGHT * niceRatio);
		int seniority = seniorityPoints(Seniority.parse(requirements.seniority()), targetSeniority);
		int workModel = workModelPoints(requirements.workModel());
		Map<String, Integer> breakdown = new LinkedHashMap<>();
		breakdown.put("obrigatorias", required);
		breakdown.put("desejaveis", nice);
		breakdown.put("senioridade", seniority);
		breakdown.put("modeloTrabalho", workModel);
		int total = Math.min(100, required + nice + seniority + workModel);
		return new Result(total, matched, gaps, breakdown, partial);
	}

	/** Soma 1 por skill atendida e {@value #PARTIAL_CREDIT} por equivalente parcial; separa o que faltou. */
	private double credit(List<String> skills, Map<String, String> owned, List<String> matched,
			List<PartialSkillMatch> partial, List<String> missing) {
		double credit = 0;
		for (String skill : skills) {
			if (owned.containsKey(skill.toLowerCase(Locale.ROOT))) {
				matched.add(skill);
				credit += 1;
				continue;
			}
			Optional<String> via = this.dictionary.partialMatch(skill, owned);
			if (via.isPresent()) {
				partial.add(new PartialSkillMatch(skill, via.get()));
				credit += PARTIAL_CREDIT;
			}
			else {
				missing.add(skill);
			}
		}
		return credit;
	}

	private static int seniorityPoints(Optional<Seniority> job, Optional<Seniority> target) {
		if (job.isEmpty() || target.isEmpty()) {
			return SENIORITY_WEIGHT / 2;
		}
		int distance = Math.abs(job.get().ordinal() - target.get().ordinal());
		return (distance == 0) ? SENIORITY_WEIGHT : (distance == 1) ? SENIORITY_WEIGHT / 2 : 0;
	}

	private static int workModelPoints(String workModel) {
		return switch (workModel == null ? "" : workModel) {
			case "REMOTO", "HIBRIDO" -> WORK_MODEL_WEIGHT;
			case "PRESENCIAL" -> 1;
			default -> 3;
		};
	}

	record Result(int score, List<String> matchedSkills, List<String> gaps, Map<String, Integer> breakdown,
			List<PartialSkillMatch> partialMatches) {
	}

}
