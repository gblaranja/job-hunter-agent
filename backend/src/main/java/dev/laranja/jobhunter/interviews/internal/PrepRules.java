package dev.laranja.jobhunter.interviews.internal;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import dev.laranja.jobhunter.interviews.InterviewDebriefView.RecurringQuestion;
import dev.laranja.jobhunter.interviews.InterviewPrepContent;
import dev.laranja.jobhunter.interviews.InterviewPrepContent.LikelyQuestion;
import dev.laranja.jobhunter.shared.TextNormalizer;
import dev.laranja.jobhunter.shared.money.MoneyFormat;
import dev.laranja.jobhunter.tracking.ApplicationStatus;
import org.jspecify.annotations.Nullable;

/**
 * Preparação montada por regras, sem a Claude API: perguntas a partir dos requisitos, das lacunas e dos
 * eliminatórios, com a história do seu banco que cita a skill; o resto sai da análise e do cadastro da empresa.
 */
final class PrepRules {

	static final int MAX_SKILL_QUESTIONS = 4;

	static final int MAX_GAP_QUESTIONS = 3;

	private PrepRules() {
	}

	static InterviewPrepContent build(PrepInputs in) {
		return new InterviewPrepContent(questions(in), technicalTopics(in), companyFacts(in), questionsToAsk(in),
				pointsToClarify(in));
	}

	private static List<LikelyQuestion> questions(PrepInputs in) {
		List<LikelyQuestion> questions = new ArrayList<>();
		List<String> covered = in.matchedSkills().isEmpty() ? in.requiredSkills() : in.matchedSkills();
		questions.add(new LikelyQuestion("Fale sobre você e a sua trajetória.", "Abertura de quase toda entrevista.",
				null, null, "Em até dois minutos: o que você faz hoje, dois resultados ligados a "
						+ joinOr(covered.stream().limit(3).toList(), "o que a vaga pede")
						+ " e por que esta vaga faz sentido agora."));
		for (String skill : covered.stream().limit(MAX_SKILL_QUESTIONS).toList()) {
			Optional<PrepInputs.Story> story = storyFor(skill, in.stories());
			String quote = in.evidence().stream()
				.filter(e -> same(e.requirement(), skill))
				.map(PrepInputs.Evidence::quote)
				.findFirst()
				.orElse(null);
			String outline = story.map(s -> "Use a história \"" + s.title() + "\": a situação em uma frase, o que você fez"
					+ " e o resultado.")
				.orElse("Escolha um projeto em que usou " + skill + ": o contexto em uma frase, a sua decisão e o resultado"
						+ " com um número.");
			if (quote != null) {
				outline += " No currículo: \"" + quote + "\".";
			}
			questions.add(new LikelyQuestion("Conte uma situação em que você usou " + skill + " para resolver um problema.",
					"Requisito obrigatório da vaga que o seu currículo atende.", story.map(PrepInputs.Story::id).orElse(null),
					story.map(PrepInputs.Story::title).orElse(null), outline));
		}
		for (String gap : in.gaps().stream().limit(MAX_GAP_QUESTIONS).toList()) {
			Optional<PrepInputs.Story> story = storyFor(gap, in.stories());
			questions.add(new LikelyQuestion("Qual a sua experiência com " + gap + "?",
					"A vaga pede e o currículo não comprova.", story.map(PrepInputs.Story::id).orElse(null),
					story.map(PrepInputs.Story::title).orElse(null),
					"Diga com franqueza até onde você conhece, cite o que usou de parecido e como aprenderia rápido. Não"
							+ " afirme uma experiência que você não tem."));
		}
		for (PrepInputs.Knockout knockout : in.knockouts()) {
			if ("NAO_ATENDE".equals(knockout.status())) {
				questions.add(new LikelyQuestion("A vaga pede: " + knockout.text() + ". Como você vê isso?",
						"Eliminatório que o seu perfil não atende" + ((knockout.reason() != null) ? ": " + knockout.reason()
								: "."),
						null, null, "Reconheça a diferença e mostre o que compensa: experiência equivalente, resultados"
								+ " ou disponibilidade para se adaptar."));
			}
		}
		if (in.stage() == ApplicationStatus.TESTE_TECNICO) {
			questions.add(new LikelyQuestion("Explique as decisões da sua solução e o que faria com mais tempo.",
					"Revisão do teste técnico.", null, null,
					"Comece pelo problema, depois as escolhas (e as alternativas que descartou), os testes e os limites"
							+ " conhecidos."));
		}
		for (PrepInputs.PastDebrief past : in.debriefs()) {
			for (String asked : past.questions()) {
				questions.add(new LikelyQuestion(asked, "Já perguntaram na etapa de " + past.interviewedOn()
						+ "; pode voltar com mais profundidade.", null, null,
						(past.toImprove() != null) ? "Da última vez faltou: " + past.toImprove() : "Aprofunde a resposta"
								+ " anterior com um exemplo concreto."));
			}
		}
		for (RecurringQuestion recurring : in.recurringQuestions().stream().limit(3).toList()) {
			questions.add(new LikelyQuestion(recurring.question(),
					"Apareceu em " + recurring.count() + " das suas entrevistas.", null, null,
					"Prepare uma resposta curta e um exemplo; ela costuma voltar."));
		}
		questions.add(new LikelyQuestion("Por que você quer trabalhar na " + in.company() + "?",
				"Pergunta de motivação, comum em todas as etapas.", null, null,
				"Ligue um fato da empresa (produto, momento, tecnologia) a algo que você quer fazer e já fez."));
		return dedupe(questions);
	}

	private static List<String> technicalTopics(PrepInputs in) {
		List<String> topics = new ArrayList<>();
		in.requiredSkills().forEach(skill -> topics.add(skill + " (obrigatória)"));
		in.niceToHave().forEach(skill -> topics.add(skill + " (desejável)"));
		in.gaps().forEach(gap -> topics.add("Fundamentos de " + gap + ", para falar com segurança do que você não usou"));
		return topics.stream().distinct().toList();
	}

	private static List<String> companyFacts(PrepInputs in) {
		List<String> facts = new ArrayList<>();
		PrepInputs.Company company = in.companyFacts();
		if (company.sector() != null) {
			facts.add("Setor: " + company.sector() + ".");
		}
		if (company.sizeRange() != null) {
			facts.add("Porte: " + company.sizeRange() + ".");
		}
		if (company.hqLocation() != null) {
			facts.add("Sede: " + company.hqLocation() + ".");
		}
		if ("CONSULTORIA_TI".equals(company.kind()) || "CONSULTORIA".equals(in.employerKind())) {
			facts.add("É uma consultoria: pergunte qual é o cliente final e como é a alocação.");
		}
		if ("RECRUTAMENTO".equals(company.kind()) || "AGENCIA".equals(in.employerKind())) {
			facts.add("A vaga veio de uma agência de recrutamento: confirme qual é a empresa contratante.");
		}
		if (company.gptw() != null) {
			facts.add("Selo GPTW: " + company.gptw() + ".");
		}
		if (company.website() != null) {
			facts.add("Site: " + company.website() + ".");
		}
		if (in.summary() != null && !in.summary().isBlank()) {
			facts.add("Sobre a vaga: " + in.summary());
		}
		if (facts.isEmpty()) {
			facts.add("Sem dados da empresa no cadastro: leia o site, o LinkedIn e notícias recentes antes da conversa.");
		}
		return facts;
	}

	private static List<String> questionsToAsk(PrepInputs in) {
		List<String> asks = new ArrayList<>(List.of("Como é o dia a dia do time e quem eu teria como referência?",
				"Quais são os maiores desafios técnicos dos próximos seis meses?",
				"Como vocês avaliam se a pessoa nesta posição foi bem nos primeiros 90 dias?",
				"Quais são as próximas etapas do processo e quando devo ter um retorno?"));
		if (!in.gaps().isEmpty()) {
			asks.add(2, "Como o time apoia quem chega sem experiência em " + in.gaps().getFirst() + "?");
		}
		return asks;
	}

	private static List<String> pointsToClarify(PrepInputs in) {
		List<String> points = new ArrayList<>();
		if (in.salaryRange() == null || in.salaryRange().isBlank()) {
			points.add("Faixa salarial: a vaga não informa." + desired(in.profile().desiredSalaryClt(), "CLT")
					+ desired(in.profile().desiredSalaryPj(), "PJ"));
		}
		if (in.contractType() == null || "NAO_INFORMADO".equals(in.contractType())) {
			points.add("Regime de contratação (CLT, PJ ou contractor); você aceita "
					+ String.join(", ", in.profile().acceptedContracts()) + ".");
		}
		else if (!in.profile().acceptedContracts().isEmpty()
				&& in.profile().acceptedContracts().stream().noneMatch(c -> c.equalsIgnoreCase(in.contractType()))) {
			points.add("A vaga é " + in.contractType() + " e você aceita " + String.join(", ", in.profile().acceptedContracts())
					+ ": confirme se há outra forma de contratação.");
		}
		switch (in.workModel()) {
			case "NAO_INFORMADO" -> points.add("Modelo de trabalho: remoto, híbrido ou presencial?");
			case "HIBRIDO" -> points.add("Quantos dias presenciais por semana e onde fica o escritório?");
			default -> {
			}
		}
		for (PrepInputs.Knockout knockout : in.knockouts()) {
			if ("SEM_INFORMACAO".equals(knockout.status())) {
				points.add("A vaga pede \"" + knockout.text() + "\": confirme se é eliminatório.");
			}
		}
		return points;
	}

	private static String desired(@Nullable BigDecimal value, String regime) {
		return (value == null) ? "" : " Sua pretensão em " + regime + ": " + MoneyFormat.amount(value, "BRL") + " por mês.";
	}

	/** A história que cita a skill entre as suas skills ou no título. */
	static Optional<PrepInputs.Story> storyFor(String skill, List<PrepInputs.Story> stories) {
		String key = TextNormalizer.normalize(skill);
		return stories.stream()
			.filter(story -> story.skills().stream().anyMatch(s -> TextNormalizer.normalize(s).equals(key)))
			.findFirst()
			.or(() -> stories.stream()
				.filter(story -> (" " + TextNormalizer.normalize(story.title() + " " + story.action()) + " ")
					.contains(" " + key + " "))
				.findFirst());
	}

	private static boolean same(String a, String b) {
		return TextNormalizer.normalize(a).equals(TextNormalizer.normalize(b));
	}

	private static List<LikelyQuestion> dedupe(List<LikelyQuestion> questions) {
		Map<String, LikelyQuestion> unique = new LinkedHashMap<>();
		questions.forEach(q -> unique.putIfAbsent(TextNormalizer.normalize(q.question()), q));
		return List.copyOf(unique.values());
	}

	private static String joinOr(List<String> values, String fallback) {
		if (values.isEmpty()) {
			return fallback;
		}
		if (values.size() == 1) {
			return values.getFirst();
		}
		return String.join(", ", values.subList(0, values.size() - 1)) + " e " + values.getLast();
	}

}
