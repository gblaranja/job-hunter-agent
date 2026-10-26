package dev.laranja.jobhunter.analysis.internal;

import java.util.ArrayList;
import java.util.List;

import dev.laranja.jobhunter.analysis.RequirementEvidence;
import dev.laranja.jobhunter.shared.TextNormalizer;
import org.jspecify.annotations.Nullable;

/**
 * Descarta as evidências que o LLM devolveu sem estarem no currículo: a citação, normalizada (caixa, acentos e
 * pontuação), precisa aparecer no texto do currículo também normalizado.
 */
final class EvidenceValidator {

	private static final int MAX_QUOTE = 200;

	private static final int MAX_ITEMS = 30;

	private EvidenceValidator() {
	}

	static List<RequirementEvidence> valid(@Nullable List<RequirementEvidence> evidence, @Nullable String resumeText) {
		if (evidence == null || resumeText == null || resumeText.isBlank()) {
			return List.of();
		}
		String resume = " " + TextNormalizer.normalize(resumeText) + " ";
		List<RequirementEvidence> result = new ArrayList<>();
		for (RequirementEvidence item : evidence) {
			if (item == null || item.requirement() == null || item.quote() == null || result.size() >= MAX_ITEMS) {
				continue;
			}
			String quote = item.quote().strip().replaceAll("\\s+", " ");
			String normalized = TextNormalizer.normalize(quote);
			if (normalized.length() < 3 || quote.length() > MAX_QUOTE * 2 || !resume.contains(" " + normalized + " ")) {
				continue;
			}
			result.add(new RequirementEvidence(item.requirement().strip(),
					(quote.length() > MAX_QUOTE) ? quote.substring(0, MAX_QUOTE) : quote));
		}
		return result;
	}

}
