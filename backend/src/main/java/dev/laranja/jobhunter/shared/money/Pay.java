package dev.laranja.jobhunter.shared.money;

import java.math.BigDecimal;

import org.jspecify.annotations.Nullable;

/**
 * Um valor de remuneração como informado na vaga ou na proposta.
 *
 * @param monthlyBenefits benefícios em reais por mês; nulo usa a premissa {@code jobhunter.salary.clt-benefits-monthly}
 * no CLT
 * @param annualBonus bônus ou PLR no ano, na mesma moeda do valor
 */
public record Pay(BigDecimal amount, String currency, PayPeriod period, PayRegime regime,
		@Nullable BigDecimal monthlyBenefits, @Nullable BigDecimal annualBonus) {

	public Pay(BigDecimal amount, String currency, PayPeriod period, PayRegime regime) {
		this(amount, currency, period, regime, null, null);
	}

}
