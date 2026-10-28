package dev.laranja.jobhunter.interviews;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class InterviewDebriefsTest {

	@Test
	void followUpIsFiveBusinessDaysLaterAtNine() {
		// quarta 21/10 → quarta 28/10; sexta 23/10 → sexta 30/10; sábado 24/10 → sexta 30/10
		assertThat(InterviewDebriefs.followUpAt(LocalDate.of(2026, 10, 21))).isEqualTo(Instant.parse("2026-10-28T12:00:00Z"));
		assertThat(InterviewDebriefs.followUpAt(LocalDate.of(2026, 10, 23))).isEqualTo(Instant.parse("2026-10-30T12:00:00Z"));
		assertThat(InterviewDebriefs.followUpAt(LocalDate.of(2026, 10, 24))).isEqualTo(Instant.parse("2026-10-30T12:00:00Z"));
	}

	@Test
	void recurringQuestionsIgnoreCaseAccentsAndPunctuation() {
		List<InterviewDebriefView.RecurringQuestion> recurring = InterviewDebriefs.recurring(List.of(
				"Por que você saiu do último emprego?", "Fale de você", "por que voce saiu do ultimo emprego",
				"Por que você saiu do último emprego!"));

		assertThat(recurring).containsExactly(
				new InterviewDebriefView.RecurringQuestion("Por que você saiu do último emprego?", 3));
	}

}
