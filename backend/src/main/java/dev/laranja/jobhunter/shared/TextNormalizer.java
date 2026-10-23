package dev.laranja.jobhunter.shared;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Normaliza textos para comparação: caixa baixa, sem acentos e com qualquer pontuação trocada por um
 * espaço simples. É a base do fingerprint das vagas e da deduplicação de empresas.
 */
public final class TextNormalizer {

	private static final Pattern DIACRITICS = Pattern.compile("\\p{M}+");

	private static final Pattern NON_ALPHANUMERIC = Pattern.compile("[^\\p{IsAlphabetic}\\p{IsDigit}]+");

	private TextNormalizer() {
	}

	public static String normalize(String text) {
		if (text == null) {
			return "";
		}
		String withoutAccents = DIACRITICS.matcher(Normalizer.normalize(text, Normalizer.Form.NFD)).replaceAll("");
		return NON_ALPHANUMERIC.matcher(withoutAccents.toLowerCase(Locale.ROOT)).replaceAll(" ").strip();
	}

}
