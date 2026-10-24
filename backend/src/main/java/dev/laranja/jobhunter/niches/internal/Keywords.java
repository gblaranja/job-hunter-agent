package dev.laranja.jobhunter.niches.internal;

import java.util.Collection;
import java.util.List;

import dev.laranja.jobhunter.shared.TextNormalizer;

/** Busca de palavras-chave inteiras em texto normalizado (sem acento, caixa baixa, pontuação virando espaço). */
final class Keywords {

	/** Expressões que contêm uma palavra-chave mas não falam do negócio ("banco de dados" não é banco). */
	private static final List<String> NOISE = List.of(" banco de dados ", " bancos de dados ", " banco relacional ",
			" bancos relacionais ", " banco nao relacional ");

	private Keywords() {
	}

	/** Texto pronto para {@link #contains}: normalizado, com espaços nas pontas e sem as expressões de ruído. */
	static String prepare(String text) {
		String prepared = " " + TextNormalizer.normalize(text) + " ";
		for (String noise : NOISE) {
			prepared = prepared.replace(noise, " ");
		}
		return prepared;
	}

	static boolean contains(String prepared, String keyword) {
		String normalized = TextNormalizer.normalize(keyword);
		return !normalized.isEmpty() && prepared.contains(" " + normalized + " ");
	}

	static boolean containsAny(String prepared, Collection<String> keywords) {
		return keywords.stream().anyMatch(keyword -> contains(prepared, keyword));
	}

	static int count(String prepared, Collection<String> keywords) {
		return (int) keywords.stream().filter(keyword -> contains(prepared, keyword)).count();
	}

}
