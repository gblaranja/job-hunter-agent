package dev.laranja.jobhunter.jobs.internal;

import java.util.List;

import dev.laranja.jobhunter.jobs.Legitimacy;
import dev.laranja.jobhunter.jobs.internal.LegitimacyAssessor.Assessment;
import dev.laranja.jobhunter.jobs.internal.LegitimacyAssessor.Signals;
import dev.laranja.jobhunter.shared.ScamSignals;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LegitimacyAssessorTest {

	private static Signals clean() {
		return new Signals(0, 0, false, 3, null, false, false, List.of());
	}

	@Test
	void freshJobWithoutSignalsIsHigh() {
		Assessment assessment = LegitimacyAssessor.evaluate(clean());

		assertThat(assessment.legitimacy()).isEqualTo(Legitimacy.ALTA);
		assertThat(assessment.reasons()).isEmpty();
	}

	@Test
	void oneOrTwoWeakSignalsAreMediumAndMoreAreLow() {
		assertThat(LegitimacyAssessor.evaluate(new Signals(0, 0, false, 45, null, false, false, List.of())))
			.extracting(Assessment::legitimacy, Assessment::reasons)
			.containsExactly(Legitimacy.MEDIA, List.of("No ar há 45 dias"));
		assertThat(LegitimacyAssessor.evaluate(new Signals(0, 0, false, 5, "GREENHOUSE", false, false, List.of())))
			.extracting(Assessment::legitimacy, Assessment::reasons)
			.containsExactly(Legitimacy.MEDIA, List.of("A empresa publica as vagas no Greenhouse e esta não aparece lá"));

		Assessment ghost = LegitimacyAssessor.evaluate(new Signals(3, 1, false, 75, null, true, true, List.of()));
		assertThat(ghost.legitimacy()).isEqualTo(Legitimacy.BAIXA);
		assertThat(ghost.reasons()).containsExactly("O link da vaga falhou na última verificação",
				"Republicada 3 vezes em 180 dias", "No ar há 75 dias", "Agência de recrutamento sem citar o cliente",
				"Descrição curta e genérica");
	}

	@Test
	void paymentOrTwoWeakScamSignalsAreSuspicious() {
		List<ScamSignals.Signal> payment = ScamSignals.detect("Pagamento antecipado do treinamento obrigatório.");
		assertThat(LegitimacyAssessor.evaluate(withScam(payment)).legitimacy()).isEqualTo(Legitimacy.SUSPEITA);

		List<ScamSignals.Signal> weak = ScamSignals.detect("Fale no WhatsApp ou mande para vagas.top@hotmail.com");
		assertThat(weak).hasSize(1);
		assertThat(LegitimacyAssessor.evaluate(withScam(weak)).legitimacy()).isEqualTo(Legitimacy.MEDIA);

		List<ScamSignals.Signal> both = List.of(new ScamSignals.Signal(ScamSignals.Kind.SO_WHATSAPP, false, "a"),
				new ScamSignals.Signal(ScamSignals.Kind.EMAIL_GRATUITO, false, "b"));
		assertThat(LegitimacyAssessor.evaluate(withScam(both)).legitimacy()).isEqualTo(Legitimacy.SUSPEITA);
	}

	private static Signals withScam(List<ScamSignals.Signal> scam) {
		return new Signals(0, 0, false, 1, null, false, false, scam);
	}

}
