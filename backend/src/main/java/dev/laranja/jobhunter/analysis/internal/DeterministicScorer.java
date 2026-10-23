package dev.laranja.jobhunter.analysis.internal;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import dev.laranja.jobhunter.analysis.Seniority;

import org.springframework.stereotype.Component;

/**
 * Score determinístico de aderência (0 a 100): obrigatórias atendidas (70), desejáveis (15), senioridade (10) e
 * modelo de trabalho (5). É explicável e não oscila entre execuções; o LLM só ajusta em cima dele.
 */
@Component
class DeterministicScorer {

	static final int REQUIRED_WEIGHT = 70;

	static final int NICE_WEIGHT = 15;

	static final int SENIORITY_WEIGHT = 10;

	static final int WORK_MODEL_WEIGHT = 5;

	Result score(JobRequirements requirements, List<String> resumeSkills, Optional<Seniority> targetSeniority) {
		Set<String> owned = resumeSkills.stream().map(skill -> skill.toLowerCase(Locale.ROOT)).collect(Collectors.toSet());
		List<String> matched = new ArrayList<>();
		List<String> gaps = new ArrayList<>();
		for (String skill : requirements.requiredSkills()) {
			(owned.contains(skill.toLowerCase(Locale.ROOT)) ? matched : gaps).add(skill);
		}
		double requiredRatio = requirements.requiredSkills().isEmpty() ? 0.5
				: (double) matched.size() / requirements.requiredSkills().size();
		long niceMatched = requirements.niceToHave().stream().filter(skill -> owned.contains(skill.toLowerCase(Locale.ROOT))).count();
		// Sem desejáveis listados, vale a mesma proporção das obrigatórias.
		double niceRatio = requirements.niceToHave().isEmpty() ? requiredRatio
				: (double) niceMatched / requirements.niceToHave().size();
		requirements.niceToHave().stream().filter(skill -> owned.contains(skill.toLowerCase(Locale.ROOT))).forEach(matched::add);

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
		return new Result(total, matched, gaps, breakdown);
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

	record Result(int score, List<String> matchedSkills, List<String> gaps, Map<String, Integer> breakdown) {
	}

}
