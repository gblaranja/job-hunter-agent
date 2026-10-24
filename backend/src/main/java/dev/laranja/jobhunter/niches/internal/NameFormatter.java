package dev.laranja.jobhunter.niches.internal;

import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

import org.jspecify.annotations.Nullable;

/**
 * Nomes das bases públicas, que chegam em caixa alta ("BANCO ITAÚ VEÍCULOS S.A."), ficam legíveis ("Banco Itaú
 * Veículos S.A."). Siglas sem vogal (TVM, BTG, XP) e as formas societárias continuam em maiúsculas.
 */
final class NameFormatter {

	private static final Set<String> LOWER = Set.of("de", "da", "do", "das", "dos", "e", "em", "para", "a", "o");

	private static final Pattern VOWEL = Pattern.compile("[AEIOUÁÉÍÓÚÂÊÔÃÕÀY]");

	private NameFormatter() {
	}

	static String pretty(String name) {
		String stripped = name.strip().replaceAll("\\s+", " ").replaceAll("\\.{2,}$", ".");
		if (!stripped.equals(stripped.toUpperCase(Locale.ROOT))) {
			return stripped;
		}
		String[] tokens = stripped.split(" ");
		StringBuilder result = new StringBuilder();
		for (int i = 0; i < tokens.length; i++) {
			if (i > 0) {
				result.append(' ');
			}
			result.append(token(tokens[i], i == 0));
		}
		return result.toString();
	}

	private static String token(String token, boolean first) {
		String lower = token.toLowerCase(Locale.ROOT);
		if (!first && LOWER.contains(lower)) {
			return lower;
		}
		// formas societárias abreviadas (S.A., S/A) e siglas sem vogal (TVM, BTG) ficam como estão; "LTDA." não
		if (((token.contains(".") || token.contains("/")) && token.replaceAll("[^\\p{L}]", "").length() <= 3)
				|| !VOWEL.matcher(token).find()) {
			return token;
		}
		StringBuilder result = new StringBuilder(lower.length());
		boolean capitalize = true;
		for (char c : lower.toCharArray()) {
			result.append(capitalize ? Character.toUpperCase(c) : c);
			capitalize = c == '-' || c == '(' || c == '&';
		}
		return result.toString();
	}

	static @Nullable String website(@Nullable String site) {
		if (site == null || site.isBlank()) {
			return null;
		}
		String lower = site.strip().toLowerCase(Locale.ROOT);
		return lower.startsWith("http") ? lower : "https://" + lower;
	}

}
