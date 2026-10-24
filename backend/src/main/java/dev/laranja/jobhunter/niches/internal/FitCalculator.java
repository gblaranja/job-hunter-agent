package dev.laranja.jobhunter.niches.internal;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import dev.laranja.jobhunter.niches.FitBand;
import dev.laranja.jobhunter.recognition.RecognitionLevel;
import org.jspecify.annotations.Nullable;

/**
 * Fit da empresa com o seu perfil, diferente do score da vaga: diz se vale acompanhar a empresa mesmo sem vaga aberta.
 * Média ponderada do subsegmento (35%), da stack nas últimas vagas (30%), do domínio (20%) e dos sinais da empresa
 * (15%), com bônus para consultorias que alocam em clientes do setor financeiro. Cada componente vira uma justificativa.
 */
public final class FitCalculator {

	/** Sem vagas coletadas, stack e domínio ficam neutros em vez de zerar o fit das empresas importadas. */
	static final int NEUTRAL = 40;

	static final int FINANCIAL_CLIENTS_BONUS = 10;

	static final List<String> JAVA = List.of("java", "spring", "spring boot");

	static final List<String> STACK_EXTRAS = List.of("microsservicos", "microsservico", "microservicos", "microservices",
			"kafka", "aws");

	static final List<String> FINANCIAL_SECTORS = List.of("banco", "bancos", "bancario", "financeiro", "financeira",
			"fintech", "fintechs", "pagamentos", "credito", "seguros", "seguradora", "investimentos", "cartoes");

	private FitCalculator() {
	}

	public static Result compute(Inputs inputs, Weights weights, List<String> domainTerms) {
		List<String> reasons = new ArrayList<>();
		reasons.add(inputs.segment());
		int segment = inputs.baseFit();

		int jobs = inputs.jobs().size();
		int stack;
		int domain;
		if (jobs == 0) {
			stack = NEUTRAL;
			domain = NEUTRAL;
			reasons.add("Sem vagas coletadas para avaliar a stack");
		}
		else {
			int javaJobs = (int) inputs.jobs().stream().filter(job -> Keywords.containsAny(job.text(), JAVA)).count();
			int extraJobs = (int) inputs.jobs().stream().filter(job -> Keywords.containsAny(job.text(), STACK_EXTRAS)).count();
			stack = (int) Math.round(100 * (0.8 * javaJobs / jobs + 0.2 * extraJobs / jobs));
			reasons.add(javaJobs + " das " + jobs + " últimas vagas pedem Java/Spring");
			Map<String, Integer> terms = new LinkedHashMap<>();
			int domainJobs = 0;
			for (JobText job : inputs.jobs()) {
				boolean any = false;
				for (String term : domainTerms) {
					if (Keywords.contains(job.text(), term)) {
						terms.merge(term, 1, Integer::sum);
						any = true;
					}
				}
				if (any) {
					domainJobs++;
				}
			}
			domain = (int) Math.round(100.0 * domainJobs / jobs);
			if (!terms.isEmpty()) {
				reasons.add("Domínio nas vagas: " + terms.entrySet()
					.stream()
					.sorted(Map.Entry.<String, Integer>comparingByValue(Comparator.reverseOrder()))
					.limit(3)
					.map(Map.Entry::getKey)
					.collect(Collectors.joining(", ")));
			}
		}

		int signals = 0;
		if (inputs.gptw() != null && inputs.gptw().visible()) {
			signals += switch (inputs.gptw()) {
				case RANKING_TI -> 40;
				case RANKING -> 30;
				default -> 20;
			};
			reasons.add(gptwLabel(inputs.gptw(), inputs.gptwYear()));
		}
		if (inputs.jobsLast90Days() >= 3) {
			signals += 30;
		}
		else if (inputs.jobsLast90Days() >= 1) {
			signals += 15;
		}
		if (inputs.jobsLast90Days() > 0) {
			reasons.add(inputs.jobsLast90Days() + ((inputs.jobsLast90Days() == 1) ? " vaga" : " vagas") + " nos últimos 90 dias");
		}
		if (jobs > 0) {
			long compatible = inputs.jobs().stream().filter(JobText::compatibleWorkModel).count();
			if (compatible * 2 >= jobs) {
				signals += 30;
				reasons.add("Remoto, híbrido ou em Ribeirão Preto em " + compatible + " de " + jobs + " vagas");
			}
		}
		signals = Math.min(100, signals);

		double total = weights.segment() + weights.stack() + weights.domain() + weights.signals();
		double score = (total <= 0) ? 0 : (weights.segment() * segment + weights.stack() * stack
				+ weights.domain() * domain + weights.signals() * signals) / total;
		if (inputs.consultancy() && inputs.clientSectors().stream().anyMatch(sector ->
				Keywords.containsAny(Keywords.prepare(sector), FINANCIAL_SECTORS))) {
			score += FINANCIAL_CLIENTS_BONUS;
			reasons.add("Aloca em clientes do setor financeiro");
		}
		int fit = (int) Math.max(0, Math.min(100, Math.round(score)));
		Map<String, Integer> components = new LinkedHashMap<>();
		components.put("segment", segment);
		components.put("stack", stack);
		components.put("domain", domain);
		components.put("signals", signals);
		return new Result(fit, FitBand.of(fit), components, reasons);
	}

	private static String gptwLabel(RecognitionLevel level, @Nullable Integer year) {
		String suffix = (year != null) ? " " + year : "";
		return switch (level) {
			case RANKING_TI -> "Ranking GPTW TI" + suffix;
			case RANKING -> "Ranking GPTW" + suffix;
			default -> "Certificada GPTW" + suffix;
		};
	}

	/**
	 * @param consultancy empresa do tipo consultoria de TI (o bônus por clientes financeiros só vale para elas)
	 * @param jobs as últimas vagas, com o texto já preparado por {@link Keywords#prepare}
	 */
	public record Inputs(String segment, int baseFit, boolean consultancy, List<String> clientSectors,
			@Nullable RecognitionLevel gptw, @Nullable Integer gptwYear, int jobsLast90Days, List<JobText> jobs) {
	}

	/** @param compatibleWorkModel remoto, híbrido ou em Ribeirão Preto */
	public record JobText(String text, boolean compatibleWorkModel) {

		public static JobText of(String rawText, boolean compatibleWorkModel) {
			return new JobText(Keywords.prepare(rawText), compatibleWorkModel);
		}

	}

	public record Weights(double segment, double stack, double domain, double signals) {
	}

	public record Result(int score, FitBand band, Map<String, Integer> components, List<String> reasons) {
	}

}
