package dev.laranja.jobhunter.shared.money;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;
import java.util.Optional;

import org.jspecify.annotations.Nullable;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Leva salários de regimes e moedas diferentes à mesma régua anual em reais:
 * <ul>
 * <li>CLT: 13,33 salários por ano (12 mais o 13º e o terço de férias), mais o FGTS de 8% sobre eles e os
 * benefícios;</li>
 * <li>PJ e contractor: o faturamento menos a alíquota de impostos e os custos fixos do mês, 12 meses por ano;</li>
 * <li>moeda estrangeira: convertida pela PTAX do Banco Central.</li>
 * </ul>
 * O valor por hora vira mês com 168 horas (21 dias de 8 horas). Um valor anual de CLT é dividido por 13,33 para chegar
 * ao salário mensal.
 */
@Component
@EnableConfigurationProperties(SalaryProperties.class)
public class SalaryMath {

	public static final BigDecimal CLT_SALARIES_PER_YEAR = new BigDecimal("13.33");

	public static final BigDecimal FGTS_RATE = new BigDecimal("0.08");

	static final BigDecimal HOURS_PER_MONTH = new BigDecimal("168");

	private static final BigDecimal TWELVE = new BigDecimal("12");

	private final SalaryProperties properties;

	private final ExchangeRates rates;

	public SalaryMath(SalaryProperties properties, ExchangeRates rates) {
		this.properties = properties;
		this.rates = rates;
	}

	/** CLT anual: 13,33 salários, FGTS de 8% sobre eles e 12 meses de benefícios. */
	public BigDecimal cltAnnual(BigDecimal monthlyGross, @Nullable BigDecimal monthlyBenefits) {
		BigDecimal salaries = monthlyGross.multiply(CLT_SALARIES_PER_YEAR);
		BigDecimal benefits = (monthlyBenefits != null) ? monthlyBenefits : this.properties.cltBenefitsMonthly();
		return money(salaries.add(salaries.multiply(FGTS_RATE)).add(benefits.multiply(TWELVE)));
	}

	/** PJ líquido no ano: (faturamento × (1 − alíquota) − custos fixos) × 12, nunca negativo. */
	public BigDecimal pjAnnualNet(BigDecimal monthlyGross) {
		BigDecimal net = monthlyGross.multiply(BigDecimal.ONE.subtract(this.properties.pjTaxRate()))
			.subtract(this.properties.pjMonthlyCosts());
		return money(net.max(BigDecimal.ZERO).multiply(TWELVE));
	}

	/**
	 * O equivalente anual em reais do valor informado.
	 * @return vazio quando a moeda é estrangeira e a PTAX não respondeu
	 */
	public Optional<SalaryEquivalent> equivalent(Pay pay) {
		String currency = pay.currency().strip().toUpperCase(Locale.ROOT);
		ExchangeRate rate = null;
		BigDecimal factor = BigDecimal.ONE;
		if (!"BRL".equals(currency)) {
			Optional<ExchangeRate> found = this.rates.brlPer(currency);
			if (found.isEmpty()) {
				return Optional.empty();
			}
			rate = found.get();
			factor = rate.brl();
		}
		BigDecimal monthly = monthly(pay.amount(), pay.period(), pay.regime()).multiply(factor);
		BigDecimal bonus = (pay.annualBonus() != null) ? pay.annualBonus().multiply(factor) : BigDecimal.ZERO;
		BigDecimal annual = switch (pay.regime()) {
			case CLT -> cltAnnual(monthly, pay.monthlyBenefits()).add(bonus);
			case PJ, CONTRACTOR -> {
				BigDecimal benefits = (pay.monthlyBenefits() != null) ? pay.monthlyBenefits() : BigDecimal.ZERO;
				yield pjAnnualNet(monthly).add(netOfTax(bonus)).add(benefits.multiply(TWELVE));
			}
		};
		annual = money(annual);
		return Optional.of(new SalaryEquivalent(annual, annual.divide(TWELVE, 2, RoundingMode.HALF_UP), rate));
	}

	/** Salário (CLT) ou faturamento (PJ) mensal na moeda original. */
	static BigDecimal monthly(BigDecimal amount, PayPeriod period, PayRegime regime) {
		return switch (period) {
			case MENSAL -> amount;
			case HORA -> amount.multiply(HOURS_PER_MONTH);
			case ANUAL -> amount.divide((regime == PayRegime.CLT) ? CLT_SALARIES_PER_YEAR : TWELVE, 10,
					RoundingMode.HALF_UP);
		};
	}

	private BigDecimal netOfTax(BigDecimal gross) {
		return gross.multiply(BigDecimal.ONE.subtract(this.properties.pjTaxRate()));
	}

	private static BigDecimal money(BigDecimal value) {
		return value.setScale(2, RoundingMode.HALF_UP);
	}

}
