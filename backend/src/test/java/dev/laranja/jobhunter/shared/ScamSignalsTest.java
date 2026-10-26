package dev.laranja.jobhunter.shared;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ScamSignalsTest {

	@Test
	void paymentRequestIsAStrongSignal() {
		assertThat(ScamSignals.detect("Para participar, pague a taxa de inscrição de R$ 49,90 via Pix."))
			.extracting(ScamSignals.Signal::kind, ScamSignals.Signal::strong)
			.containsExactly(org.assertj.core.groups.Tuple.tuple(ScamSignals.Kind.PAGAMENTO, true));
		assertThat(ScamSignals.detect("Necessário comprar o kit de trabalho antes de começar.")).hasSize(1);
	}

	@Test
	void whatsappOnlyAndFreeEmailAreWeakSignals() {
		assertThat(ScamSignals.detect("Interessados chamar no WhatsApp (11) 98888-7777."))
			.extracting(ScamSignals.Signal::kind)
			.containsExactly(ScamSignals.Kind.SO_WHATSAPP);
		assertThat(ScamSignals.detect("Envie o currículo para rh.bancoalfa@gmail.com"))
			.extracting(ScamSignals.Signal::kind, ScamSignals.Signal::strong)
			.containsExactly(org.assertj.core.groups.Tuple.tuple(ScamSignals.Kind.EMAIL_GRATUITO, false));
	}

	@Test
	void ordinaryPostingsAndMissingSalaryAreClean() {
		assertThat(ScamSignals.detect("""
				Desenvolvedor Java sênior. Salário a combinar. Candidate-se em https://boards.greenhouse.io/acme/jobs/1
				ou fale com recrutamento@acme.com.br; dúvidas também pelo WhatsApp do RH.""")).isEmpty();
		assertThat(ScamSignals.detect(null)).isEmpty();
		assertThat(ScamSignals.detect("Benefícios: plano de saúde sem taxa de coparticipação")).isEmpty();
	}

}
