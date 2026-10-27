package dev.laranja.jobhunter.shared.money;

import java.math.BigDecimal;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Premissas da comparação entre CLT e PJ.
 *
 * @param pjTaxRate fração do faturamento PJ que vai para impostos (Simples Nacional e pró-labore)
 * @param pjMonthlyCosts custos fixos do PJ por mês, em reais (contador, taxas, plano de saúde por conta própria)
 * @param cltBenefitsMonthly benefícios do CLT por mês, em reais, quando a vaga ou a proposta não informa
 */
@ConfigurationProperties("jobhunter.salary")
public record SalaryProperties(@DefaultValue("0.15") BigDecimal pjTaxRate,
		@DefaultValue("400") BigDecimal pjMonthlyCosts, @DefaultValue("1000") BigDecimal cltBenefitsMonthly,
		@DefaultValue Ptax ptax) {

	/** API Olinda do Banco Central com a PTAX. */
	public record Ptax(@DefaultValue("https://olinda.bcb.gov.br/olinda/servico/PTAX/versao/v1/odata") String baseUrl) {
	}

}
