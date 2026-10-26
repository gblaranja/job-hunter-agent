package dev.laranja.jobhunter.jobs.web;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.Optional;

import dev.laranja.jobhunter.shared.money.ExchangeRate;
import dev.laranja.jobhunter.shared.money.ExchangeRates;
import dev.laranja.jobhunter.shared.money.Pay;
import dev.laranja.jobhunter.shared.money.PayPeriod;
import dev.laranja.jobhunter.shared.money.PayRegime;
import dev.laranja.jobhunter.shared.money.SalaryEquivalent;
import dev.laranja.jobhunter.shared.money.SalaryMath;
import dev.laranja.jobhunter.shared.money.SalaryProperties;
import org.jspecify.annotations.Nullable;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * O salário da vaga contra o seu piso ({@code candidate_profile.desired_salary_clt} e {@code desired_salary_pj}), os
 * dois no equivalente anual do {@link SalaryMath}. Vaga sem salário, ou em moeda sem câmbio, nunca fica abaixo do
 * piso. O filtro da caixa repete a conta em SQL ({@link #ANNUAL_SQL}), para paginar no banco.
 */
@Component
class SalaryFloor {

	/** Equivalente anual do maior valor da faixa (ou do único informado), na mesma conta do SalaryMath. */
	static final String ANNUAL_SQL = """
			(with s as (select coalesce((an.salary->>'max')::numeric, (an.salary->>'min')::numeric) as amount,
			                   upper(coalesce(an.salary->>'currency', 'BRL')) as currency,
			                   coalesce(an.salary->>'period', 'MENSAL') as period,
			                   coalesce(an.salary->>'regime',
			                            case when upper(coalesce(an.salary->>'currency', 'BRL')) = 'BRL' then 'CLT'
			                                 else 'CONTRACTOR' end) as regime),
			      m as (select s.regime, s.amount
			                   * case s.period when 'HORA' then 168
			                                   when 'ANUAL' then 1 / case s.regime when 'CLT' then 13.33 else 12 end
			                                   else 1 end
			                   * case s.currency when 'BRL' then 1 when 'USD' then cast(:usd as numeric)
			                                     when 'EUR' then cast(:eur as numeric) end as monthly
			            from s)
			 select case when m.regime = 'CLT' then m.monthly * 13.33 * 1.08 + :cltBenefits * 12
			             else greatest(m.monthly * (1 - :pjTax) - :pjCosts, 0) * 12 end
			 from m)
			""";

	private final JdbcClient jdbc;

	private final SalaryMath math;

	private final SalaryProperties properties;

	private final ExchangeRates rates;

	SalaryFloor(JdbcClient jdbc, SalaryMath math, SalaryProperties properties, ExchangeRates rates) {
		this.jdbc = jdbc;
		this.math = math;
		this.properties = properties;
		this.rates = rates;
	}

	/** Pisos anuais em CLT e em PJ; o que faltar usa o outro. Vazio sem pretensão cadastrada. */
	Floors floors() {
		record Desired(@Nullable BigDecimal clt, @Nullable BigDecimal pj) {
		}
		Desired desired = this.jdbc.sql("select desired_salary_clt, desired_salary_pj from candidate_profile where id = 1")
			.query((rs, row) -> new Desired(rs.getBigDecimal("desired_salary_clt"), rs.getBigDecimal("desired_salary_pj")))
			.optional()
			.orElse(new Desired(null, null));
		BigDecimal clt = (desired.clt() != null) ? this.math.cltAnnual(desired.clt(), null) : null;
		BigDecimal pj = (desired.pj() != null) ? this.math.pjAnnualNet(desired.pj()) : null;
		return new Floors((clt != null) ? clt : pj, (pj != null) ? pj : clt);
	}

	/** Parâmetros de {@link #ANNUAL_SQL}; moeda sem câmbio fica nula e a vaga passa no filtro. */
	SqlParams sqlParams() {
		return new SqlParams(rate("USD"), rate("EUR"), this.properties.cltBenefitsMonthly(), this.properties.pjTaxRate(),
				this.properties.pjMonthlyCosts());
	}

	/** Equivalente anual do salário extraído da vaga; nulo sem salário ou sem câmbio. */
	@Nullable SalaryEquivalent equivalent(JobDetail.@Nullable Salary salary) {
		if (salary == null) {
			return null;
		}
		BigDecimal amount = (salary.max() != null) ? salary.max() : salary.min();
		if (amount == null || amount.signum() <= 0) {
			return null;
		}
		String currency = salary.currency().toUpperCase(Locale.ROOT);
		PayRegime regime = (salary.regime() != null) ? PayRegime.valueOf(salary.regime())
				: "BRL".equals(currency) ? PayRegime.CLT : PayRegime.CONTRACTOR;
		PayPeriod period = PayPeriod.valueOf(salary.period());
		return this.math.equivalent(new Pay(amount, currency, period, regime)).orElse(null);
	}

	/** A vaga paga menos que o piso do regime dela; nulo quando não dá para saber. */
	static @Nullable Boolean below(@Nullable SalaryEquivalent equivalent, JobDetail.@Nullable Salary salary,
			Floors floors) {
		if (equivalent == null || salary == null) {
			return null;
		}
		BigDecimal floor = isClt(salary) ? floors.clt() : floors.pj();
		return (floor == null) ? null : equivalent.annualBrl().compareTo(floor) < 0;
	}

	static boolean isClt(JobDetail.Salary salary) {
		return (salary.regime() != null) ? "CLT".equals(salary.regime())
				: "BRL".equalsIgnoreCase(salary.currency());
	}

	private @Nullable BigDecimal rate(String currency) {
		Optional<ExchangeRate> rate = this.rates.brlPer(currency);
		return rate.map(ExchangeRate::brl).orElse(null);
	}

	record Floors(@Nullable BigDecimal clt, @Nullable BigDecimal pj) {

		boolean any() {
			return this.clt != null || this.pj != null;
		}

	}

	record SqlParams(@Nullable BigDecimal usd, @Nullable BigDecimal eur, BigDecimal cltBenefits, BigDecimal pjTax,
			BigDecimal pjCosts) {
	}

}
