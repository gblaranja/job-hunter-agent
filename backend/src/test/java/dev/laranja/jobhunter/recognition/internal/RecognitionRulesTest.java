package dev.laranja.jobhunter.recognition.internal;

import java.time.LocalDate;
import java.util.List;

import dev.laranja.jobhunter.recognition.RecognitionLevel;
import dev.laranja.jobhunter.recognition.internal.GptwVerifier.Verdict;
import dev.laranja.jobhunter.recognition.internal.RecognitionRules.Decision;
import dev.laranja.jobhunter.recognition.internal.RecognitionRules.Review;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RecognitionRulesTest {

	private static final LocalDate TODAY = LocalDate.of(2026, 10, 23);

	private static final String URL = "https://gptw.com.br/empresa/alfa";

	@Test
	void withoutTheYearItIsNotFound() {
		Decision decision = decide(verdict("CERTIFICACAO", 0, URL, "Alfa", 0.95), List.of());

		assertThat(decision.level()).isEqualTo(RecognitionLevel.NAO_ENCONTRADA);
		assertThat(decision.rationale()).contains("ano");
	}

	@Test
	void evidenceMustBeOneOfTheResults() {
		Decision decision = decide(verdict("RANKING_TI", 2026, "https://inventado.com/alfa", "Alfa", 0.95), List.of());

		assertThat(decision.level()).isEqualTo(RecognitionLevel.NAO_ENCONTRADA);
	}

	@Test
	void withoutTheCompanyNameItIsNotFound() {
		assertThat(decide(verdict("RANKING", 2026, URL, "", 0.95), List.of()).level())
			.isEqualTo(RecognitionLevel.NAO_ENCONTRADA);
	}

	@Test
	void recentItRankingWithHighConfidenceIsAccepted() {
		Decision decision = decide(verdict("RANKING_TI", 2025, URL, "Alfa S.A.", 0.92), List.of());

		assertThat(decision.level()).isEqualTo(RecognitionLevel.RANKING_TI);
		assertThat(decision.review()).isEqualTo(Review.AUTOMATICO);
		assertThat(decision.validUntil()).isEqualTo(LocalDate.of(2026, 12, 31));
	}

	@Test
	void oldRecognitionsExpire() {
		assertThat(decide(verdict("RANKING_TI", 2024, URL, "Alfa", 0.92), List.of()).level())
			.isEqualTo(RecognitionLevel.EXPIRADA);
		assertThat(decide(verdict("CERTIFICACAO", 2024, URL, "Alfa", 0.92), List.of()).level())
			.isEqualTo(RecognitionLevel.EXPIRADA);
	}

	@Test
	void writtenValidityWins() {
		Verdict verdict = new Verdict("CERTIFICACAO", 2025, "", 0, "2026-03-01", URL, "", "Alfa", 0.9, "");

		assertThat(decide(verdict, List.of()).level()).isEqualTo(RecognitionLevel.EXPIRADA);
	}

	@Test
	void lowConfidenceGoesToReview() {
		Decision decision = decide(verdict("CERTIFICACAO", 2026, URL, "Alfa", 0.6), List.of());

		assertThat(decision.level()).isEqualTo(RecognitionLevel.CERTIFICADA);
		assertThat(decision.review()).isEqualTo(Review.PENDENTE);
	}

	@Test
	void homonymsGoToReview() {
		Decision similarCompany = decide(verdict("RANKING", 2026, URL, "Alfa", 0.95), List.of("Alfa Pagamentos"));
		Decision otherName = decide(verdict("RANKING", 2026, URL, "Alfa Seguros", 0.95), List.of());

		assertThat(similarCompany.review()).isEqualTo(Review.PENDENTE);
		assertThat(similarCompany.rationale()).contains("Alfa Pagamentos");
		assertThat(otherName.review()).isEqualTo(Review.PENDENTE);
		assertThat(otherName.rationale()).contains("Alfa Seguros");
	}

	@Test
	void anAliasIsTheSameCompany() {
		Decision decision = RecognitionRules.decide(verdict("RANKING", 2026, URL, "Alfa Tech", 0.95), List.of(URL), "alfa",
				List.of("alfa tech"), List.of(), TODAY, 0.8);

		assertThat(decision.review()).isEqualTo(Review.AUTOMATICO);
	}

	private static Decision decide(Verdict verdict, List<String> homonyms) {
		return RecognitionRules.decide(verdict, List.of(URL), "alfa", List.of(), homonyms, TODAY, 0.8);
	}

	private static Verdict verdict(String kind, int year, String url, String matchedName, double confidence) {
		return new Verdict(kind, year, "GPTW TI " + year, 0, "", url, "Alfa está entre as melhores", matchedName,
				confidence, "Página oficial do GPTW.");
	}

}
