package dev.laranja.jobhunter.companies;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import dev.laranja.jobhunter.shared.TextNormalizer;

/**
 * Nome da empresa para deduplicação: sem acentos, caixa e pontuação, e sem sufixos societários ou de país
 * ("Ltda", "S.A.", "Brasil"). "Itaú Unibanco S.A." e "ITAU UNIBANCO" viram o mesmo nome.
 */
public final class CompanyNameNormalizer {

	private static final Set<String> TRAILING_NOISE = Set.of("ltda", "me", "epp", "eireli", "inc", "llc", "ltd",
			"corp", "gmbh", "brasil", "brazil", "do", "da", "de", "sa");

	private CompanyNameNormalizer() {
	}

	public static String normalize(String name) {
		String normalized = TextNormalizer.normalize(name);
		// "S.A." e "S/A" chegam aqui como "s a"; junta antes de remover os sufixos.
		normalized = normalized.replaceAll("(^| )s a( |$)", "$1sa$2");
		List<String> tokens = new ArrayList<>(Arrays.asList(normalized.split(" ")));
		while (tokens.size() > 1 && TRAILING_NOISE.contains(tokens.getLast())) {
			tokens.removeLast();
		}
		return String.join(" ", tokens).strip();
	}

}
