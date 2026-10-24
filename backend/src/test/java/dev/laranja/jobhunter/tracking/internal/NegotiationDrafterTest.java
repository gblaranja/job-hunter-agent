package dev.laranja.jobhunter.tracking.internal;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import dev.laranja.jobhunter.shared.money.PayPeriod;
import dev.laranja.jobhunter.shared.money.PayRegime;
import dev.laranja.jobhunter.tracking.NegotiationDraft;
import dev.laranja.jobhunter.tracking.OfferDecision;
import dev.laranja.jobhunter.tracking.OfferView;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class NegotiationDrafterTest {

	private static final OfferView OFFER = offer(1, "15000", "BRL");

	private static final OfferView OTHER = offer(2, "17500", "BRL");

	@Test
	void acceptsOnlyTheNumbersYouInformed() {
		NegotiationDraft.Request request = new NegotiationDraft.Request(new BigDecimal("18000"), "Tenho 8 anos de Java.");

		assertThat(NegotiationDrafter.onlyKnownNumbers("De R$ 15.000 para R$ 18.000 por mês, com 8 anos de Java.", OFFER,
				List.of(OTHER), request)).isTrue();
		assertThat(NegotiationDrafter.onlyKnownNumbers("Tenho outra proposta de R$ 17.500,00.", OFFER, List.of(OTHER),
				request)).isTrue();
		assertThat(NegotiationDrafter.onlyKnownNumbers("O mercado paga R$ 20.000 por mês.", OFFER, List.of(OTHER),
				request)).isFalse();
		assertThat(NegotiationDrafter.onlyKnownNumbers("Peço 18000.", OFFER, List.of(), new NegotiationDraft.Request(null,
				null))).isFalse();
	}

	@Test
	void templateUsesTheOfferAndTheTarget() {
		String text = NegotiationDrafter.template(OFFER, new NegotiationDraft.Request(null, null));

		assertThat(text).contains("Desenvolvedor Java na Banco Alfa", "R$ 15.000 por mês", "espaço para revisar")
			.doesNotContain("null");
	}

	private static OfferView offer(long id, String base, String currency) {
		return new OfferView(id, id, id, "Desenvolvedor Java", "Banco Alfa", PayRegime.PJ, new BigDecimal(base), currency,
				PayPeriod.MENSAL, null, null, null, null, null, null, null, OfferDecision.PENDENTE, null, null, null, null,
				null, Instant.EPOCH);
	}

}
