package dev.laranja.jobhunter.shared.money;

import java.math.BigDecimal;

import org.jspecify.annotations.Nullable;

/**
 * Um salário levado à mesma régua: o valor anual em reais que a pessoa recebe no regime, para comparar CLT com PJ.
 *
 * @param annualBrl CLT: 13,33 salários mais FGTS e benefícios; PJ e contractor: líquido de impostos e custos fixos
 * @param monthlyBrl {@code annualBrl} dividido por 12
 * @param exchangeRate câmbio usado; nulo em reais
 */
public record SalaryEquivalent(BigDecimal annualBrl, BigDecimal monthlyBrl, @Nullable ExchangeRate exchangeRate) {
}
