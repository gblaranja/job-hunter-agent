package dev.laranja.jobhunter.recognition.internal;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Collection;
import java.util.List;

import dev.laranja.jobhunter.companies.CompanyNameNormalizer;
import dev.laranja.jobhunter.recognition.RecognitionLevel;
import dev.laranja.jobhunter.recognition.internal.GptwVerifier.Verdict;
import org.jspecify.annotations.Nullable;

/**
 * Regras determinísticas sobre o veredito da IA: sem ano, sem evidência entre os resultados ou sem o nome da empresa,
 * o resultado é {@code NAO_ENCONTRADA}; o nível sai do tipo, do ano e da validade; confiança baixa ou homônimo vão
 * para a revisão.
 */
public final class RecognitionRules {

	private RecognitionRules() {
	}

	public static Decision decide(Verdict verdict, Collection<String> resultUrls, String companyNormalizedName,
			Collection<String> aliases, List<String> homonyms, LocalDate today, double reviewThreshold) {
		double confidence = Math.max(0, Math.min(1, verdict.confidence()));
		String kind = (verdict.kind() == null) ? "" : verdict.kind().strip();
		if (!List.of("RANKING_TI", "RANKING", "CERTIFICACAO").contains(kind)) {
			return notFound(verdict.rationale());
		}
		if (verdict.year() <= 0 || verdict.year() > today.getYear()) {
			return notFound("Sem o ano do reconhecimento na evidência.");
		}
		if (blank(verdict.evidenceUrl()) || !resultUrls.contains(verdict.evidenceUrl().strip())) {
			return notFound("A evidência não está entre os resultados da busca.");
		}
		if (blank(verdict.matchedName())) {
			return notFound("A evidência não cita a empresa explicitamente.");
		}
		LocalDate validUntil = validUntil(verdict);
		RecognitionLevel level;
		if (validUntil.isBefore(today)) {
			level = RecognitionLevel.EXPIRADA;
		}
		else if ("RANKING_TI".equals(kind)) {
			level = (verdict.year() >= today.getYear() - 1) ? RecognitionLevel.RANKING_TI : RecognitionLevel.RANKING;
		}
		else if ("RANKING".equals(kind)) {
			level = RecognitionLevel.RANKING;
		}
		else {
			level = RecognitionLevel.CERTIFICADA;
		}
		String matched = CompanyNameNormalizer.normalize(verdict.matchedName());
		boolean sameName = matched.equals(companyNormalizedName) || aliases.contains(matched);
		boolean homonym = !sameName || !homonyms.isEmpty();
		Review review = (!level.visible() || (confidence >= reviewThreshold && !homonym)) ? Review.AUTOMATICO
				: Review.PENDENTE;
		String rationale = verdict.rationale();
		if (homonym) {
			rationale = ((rationale == null) ? "" : rationale + " ")
					+ (sameName ? "Há empresas com nome parecido: " + String.join(", ", homonyms) + "."
							: "A evidência cita \"" + verdict.matchedName().strip() + "\".");
		}
		return new Decision(level, verdict.year(), validUntil, blankToNull(verdict.rankingName()),
				(verdict.rankingPosition() > 0) ? verdict.rankingPosition() : null, verdict.evidenceUrl().strip(),
				blankToNull(verdict.evidenceExcerpt()), verdict.matchedName().strip(), confidence, review, rationale);
	}

	/**
	 * Validade escrita na evidência ou, sem ela, o fim do ano seguinte: assim um ranking de 2025 conta como "do ano
	 * atual ou do anterior" até dezembro de 2026, e uma certificação dura cerca de um ano.
	 */
	static LocalDate validUntil(Verdict verdict) {
		if (!blank(verdict.validUntil())) {
			try {
				return LocalDate.parse(verdict.validUntil().strip());
			}
			catch (DateTimeParseException ex) {
				// segue com o padrão
			}
		}
		return LocalDate.of(verdict.year() + 1, 12, 31);
	}

	private static Decision notFound(@Nullable String rationale) {
		return new Decision(RecognitionLevel.NAO_ENCONTRADA, null, null, null, null, null, null, null, 1.0,
				Review.AUTOMATICO, rationale);
	}

	private static boolean blank(@Nullable String value) {
		return value == null || value.isBlank();
	}

	private static @Nullable String blankToNull(@Nullable String value) {
		return blank(value) ? null : value.strip();
	}

	public enum Review {

		AUTOMATICO, PENDENTE

	}

	public record Decision(RecognitionLevel level, @Nullable Integer year, @Nullable LocalDate validUntil,
			@Nullable String rankingName, @Nullable Integer rankingPosition, @Nullable String evidenceUrl,
			@Nullable String evidenceExcerpt, @Nullable String matchedName, double confidence, Review review,
			@Nullable String rationale) {
	}

}
