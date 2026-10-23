package dev.laranja.jobhunter.jobs.internal;

import java.util.Set;

import dev.laranja.jobhunter.jobs.JobScope;
import dev.laranja.jobhunter.shared.TextNormalizer;
import org.jspecify.annotations.Nullable;

/**
 * Classifica a vaga como nacional ou internacional pelo idioma da descrição: conta palavras funcionais do inglês
 * e do português, que aparecem em qualquer texto corrido, e só chama de inglês o texto em que elas predominam com
 * folga. Termos técnicos em inglês numa vaga brasileira não mudam o resultado; descrição curta ou bilíngue fica
 * como nacional.
 */
public final class JobScopeDetector {

	// Sem as que existem nas duas línguas ("a", "do", "no", "as", "se"...) e já sem acentos, como o normalizador deixa.
	private static final Set<String> ENGLISH = Set.of("the", "and", "with", "for", "you", "your", "our", "we", "are",
			"is", "will", "of", "in", "on", "an", "at", "be", "or", "this", "that", "from", "have", "has", "who",
			"what", "about", "their", "they", "us", "can", "would", "should", "into", "within");

	private static final Set<String> PORTUGUESE = Set.of("de", "da", "das", "dos", "que", "para", "com", "em", "uma",
			"um", "os", "nao", "voce", "sao", "nossa", "nosso", "nossos", "mais", "como", "por", "ou", "na", "nas",
			"nos", "pelo", "pela", "ser", "sua", "seu", "seus", "suas", "ao", "aos", "sobre", "tambem", "entre", "esta",
			"estamos", "sera");

	/** Palavras funcionais necessárias para arriscar um idioma. */
	private static final int MIN_HITS = 8;

	private JobScopeDetector() {
	}

	public static JobScope detect(@Nullable String description) {
		int english = 0;
		int portuguese = 0;
		for (String word : TextNormalizer.normalize(description).split(" ")) {
			if (ENGLISH.contains(word)) {
				english++;
			}
			else if (PORTUGUESE.contains(word)) {
				portuguese++;
			}
		}
		boolean enoughText = english + portuguese >= MIN_HITS;
		return (enoughText && english > 2 * portuguese) ? JobScope.INTERNACIONAL : JobScope.NACIONAL;
	}

}
