package dev.laranja.jobhunter.shared.money;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Quantos reais vale uma unidade da moeda.
 *
 * @param date dia da cotação (a PTAX do último dia útil disponível)
 */
public record ExchangeRate(String currency, BigDecimal brl, LocalDate date) {
}
