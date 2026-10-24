package dev.laranja.jobhunter.shared.money;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SalaryMathTest {

	private static final ExchangeRate DOLLAR = new ExchangeRate("USD", new BigDecimal("5.00"), LocalDate.of(2026, 10, 23));

	private final SalaryProperties properties = new SalaryProperties(new BigDecimal("0.15"), new BigDecimal("400"),
			new BigDecimal("1000"), new SalaryProperties.Ptax("http://localhost"));

	private final SalaryMath math = new SalaryMath(this.properties,
			currency -> "USD".equals(currency) ? Optional.of(DOLLAR) : Optional.empty());

	@Test
	void cltCountsThirteenPointThirtyThreeSalariesFgtsAndBenefits() {
		// 10.000 × 13,33 = 133.300; FGTS 8% = 10.664; benefícios 1.000 × 12 = 12.000
		assertThat(this.math.cltAnnual(new BigDecimal("10000"), null)).isEqualByComparingTo("155964.00");
		assertThat(this.math.cltAnnual(new BigDecimal("10000"), new BigDecimal("1500"))).isEqualByComparingTo("161964.00");
	}

	@Test
	void pjIsNetOfTaxAndFixedCosts() {
		// (15.000 × 0,85 − 400) × 12 = 148.200
		assertThat(this.math.pjAnnualNet(new BigDecimal("15000"))).isEqualByComparingTo("148200.00");
		assertThat(this.math.pjAnnualNet(new BigDecimal("100"))).isEqualByComparingTo("0.00");
	}

	@Test
	void pjOfferComparesWithCltOnTheSameAnnualScale() {
		SalaryEquivalent clt = this.math.equivalent(new Pay(new BigDecimal("10000"), "BRL", PayPeriod.MENSAL, PayRegime.CLT))
			.orElseThrow();
		SalaryEquivalent pj = this.math.equivalent(new Pay(new BigDecimal("15000"), "BRL", PayPeriod.MENSAL, PayRegime.PJ))
			.orElseThrow();

		assertThat(clt.annualBrl()).isEqualByComparingTo("155964.00");
		assertThat(clt.monthlyBrl()).isEqualByComparingTo("12997.00");
		assertThat(pj.annualBrl()).isEqualByComparingTo("148200.00");
		assertThat(pj.exchangeRate()).isNull();
	}

	@Test
	void foreignCurrencyUsesPtaxAndHourlyBecomesMonthly() {
		// US$ 40/h × 168 h = US$ 6.720 × 5 = R$ 33.600 por mês; (33.600 × 0,85 − 400) × 12 = 337.920
		SalaryEquivalent contractor = this.math
			.equivalent(new Pay(new BigDecimal("40"), "usd", PayPeriod.HORA, PayRegime.CONTRACTOR))
			.orElseThrow();

		assertThat(contractor.annualBrl()).isEqualByComparingTo("337920.00");
		assertThat(contractor.exchangeRate()).isEqualTo(DOLLAR);
	}

	@Test
	void annualCltIsDividedByThirteenPointThirtyThreeAndBonusIsAdded() {
		// 133.300 por ano = 10.000 por mês; mais o bônus de 20.000
		SalaryEquivalent clt = this.math.equivalent(new Pay(new BigDecimal("133300"), "BRL", PayPeriod.ANUAL,
				PayRegime.CLT, BigDecimal.ZERO, new BigDecimal("20000")))
			.orElseThrow();

		assertThat(clt.annualBrl()).isEqualByComparingTo("163964.00");
	}

	@Test
	void withoutExchangeRateThereIsNoEquivalent() {
		assertThat(this.math.equivalent(new Pay(new BigDecimal("5000"), "EUR", PayPeriod.MENSAL, PayRegime.CONTRACTOR)))
			.isEmpty();
	}

}
