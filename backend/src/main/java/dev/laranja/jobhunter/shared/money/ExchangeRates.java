package dev.laranja.jobhunter.shared.money;

import java.util.Optional;

/** Fonte do câmbio para converter salários em moeda estrangeira. */
public interface ExchangeRates {

	/** Cotação da moeda em reais; vazio quando a fonte não respondeu ("sem câmbio"). */
	Optional<ExchangeRate> brlPer(String currency);

}
