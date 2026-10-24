package dev.laranja.jobhunter.analysis.internal;

import java.time.Year;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import dev.laranja.jobhunter.profile.EnglishLevel;
import dev.laranja.jobhunter.shared.TextNormalizer;
import org.jspecify.annotations.Nullable;

/**
 * Leituras simples de um texto, sem LLM: anos de experiência pelos períodos do currículo e o nível de inglês
 * declarado. Servem de sugestão para o perfil da pessoa e de regra para os eliminatórios das vagas.
 */
final class ResumeHints {

	/** "03/2018 - atual", "2015 a 2019", "jan 2018 – presente": ano inicial e final de um período. */
	private static final Pattern PERIOD = Pattern.compile("((?:19|20)\\d{2}) (?:a |ate |to |)(?:[a-z]{3,9} (?:de )?|\\d{1,2} )?"
			+ "((?:19|20)\\d{2}|atual|atualmente|presente|hoje|o momento|current|present|now)");

	private static final Pattern ENGLISH_THEN_LEVEL = Pattern
		.compile("(ingles|english)(?: [a-z]+){0,3}? (fluente|fluent|fluencia|fluency|nativo|native|avancado|advanced"
				+ "|intermediario|intermediate|conversacao|conversational|basico|basic|tecnico|technical|c1|c2|b1|b2|a1|a2)");

	private static final Pattern LEVEL_THEN_ENGLISH = Pattern
		.compile("(fluente|fluent|fluencia|fluency|nativo|native|avancado|advanced|intermediario|intermediate|basico"
				+ "|basic|tecnico|technical)(?: [a-z]+){0,2}? (?:em |no |in )?(ingles|english)");

	private ResumeHints() {
	}

	/** Do início do primeiro período até hoje; vazio se o texto não traz períodos com ano. */
	static Optional<Integer> yearsOfExperience(String text, Year now) {
		Matcher matcher = PERIOD.matcher(TextNormalizer.normalize(text));
		int first = Integer.MAX_VALUE;
		while (matcher.find()) {
			int start = Integer.parseInt(matcher.group(1));
			String endText = matcher.group(2);
			int end = Character.isDigit(endText.charAt(0)) ? Integer.parseInt(endText) : now.getValue();
			if (end >= start && start <= now.getValue()) {
				first = Math.min(first, start);
			}
		}
		if (first == Integer.MAX_VALUE) {
			return Optional.empty();
		}
		return Optional.of(Math.max(0, Math.min(50, now.getValue() - first)));
	}

	/** O nível de inglês mais alto citado no texto. */
	static Optional<EnglishLevel> englishLevel(String text) {
		String normalized = TextNormalizer.normalize(text);
		EnglishLevel best = null;
		for (Pattern pattern : new Pattern[] { ENGLISH_THEN_LEVEL, LEVEL_THEN_ENGLISH }) {
			Matcher matcher = pattern.matcher(normalized);
			while (matcher.find()) {
				String word = (pattern == ENGLISH_THEN_LEVEL) ? matcher.group(2) : matcher.group(1);
				EnglishLevel level = level(word);
				if (level != null && (best == null || level.ordinal() > best.ordinal())) {
					best = level;
				}
			}
		}
		return Optional.ofNullable(best);
	}

	/** Converte a palavra (ou o nível CEFR) num {@link EnglishLevel}; nulo se não for uma palavra conhecida. */
	static @Nullable EnglishLevel level(String word) {
		return switch (word.toLowerCase(Locale.ROOT)) {
			case "fluente", "fluent", "fluencia", "fluency", "nativo", "native", "c2" -> EnglishLevel.FLUENTE;
			case "avancado", "advanced", "c1" -> EnglishLevel.AVANCADO;
			case "intermediario", "intermediate", "conversacao", "conversational", "b1", "b2" -> EnglishLevel.INTERMEDIARIO;
			case "basico", "basic", "tecnico", "technical", "a1", "a2" -> EnglishLevel.BASICO;
			case "nenhum", "none" -> EnglishLevel.NENHUM;
			default -> null;
		};
	}

	/** Lê o nível vindo do LLM; vazio se não for um dos valores conhecidos. */
	static Optional<EnglishLevel> parseEnglish(String value) {
		if (value == null || value.isBlank()) {
			return Optional.empty();
		}
		try {
			return Optional.of(EnglishLevel.valueOf(TextNormalizer.normalize(value).toUpperCase(Locale.ROOT)));
		}
		catch (IllegalArgumentException ex) {
			return Optional.ofNullable(level(TextNormalizer.normalize(value)));
		}
	}

}
