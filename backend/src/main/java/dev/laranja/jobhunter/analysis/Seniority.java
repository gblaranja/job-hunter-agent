package dev.laranja.jobhunter.analysis;

import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

import dev.laranja.jobhunter.shared.TextNormalizer;

/** Senioridade de uma vaga ou do cargo-alvo de um currículo, em ordem crescente. */
public enum Seniority {

	ESTAGIO("estagio|estagiario|intern|internship"),

	JUNIOR("junior|jr"),

	PLENO("pleno|pl|mid level|mid"),

	SENIOR("senior|sr"),

	ESPECIALISTA("especialista|staff|principal|lead|tech lead|lider tecnico|arquiteto|architect");

	private final Pattern pattern;

	Seniority(String words) {
		this.pattern = Pattern.compile("(^| )(" + words + ")( |$)");
	}

	/** Detecta pelo texto (normalmente o título); o nível mais alto mencionado vence ("Pleno/Sênior" = sênior). */
	public static Optional<Seniority> detect(String text) {
		if (text == null) {
			return Optional.empty();
		}
		String normalized = TextNormalizer.normalize(text);
		Seniority found = null;
		for (Seniority level : values()) {
			if (level.pattern.matcher(normalized).find()) {
				found = level;
			}
		}
		return Optional.ofNullable(found);
	}

	public static Optional<Seniority> parse(String value) {
		if (value == null) {
			return Optional.empty();
		}
		try {
			return Optional.of(valueOf(TextNormalizer.normalize(value).toUpperCase(Locale.ROOT).replace(' ', '_')));
		}
		catch (IllegalArgumentException ex) {
			return detect(value);
		}
	}

}
