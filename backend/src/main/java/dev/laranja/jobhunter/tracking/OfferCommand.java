package dev.laranja.jobhunter.tracking;

import java.math.BigDecimal;
import java.time.LocalDate;

import dev.laranja.jobhunter.jobs.WorkModel;
import dev.laranja.jobhunter.shared.money.PayPeriod;
import dev.laranja.jobhunter.shared.money.PayRegime;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.jspecify.annotations.Nullable;

/**
 * Os números de uma proposta, como a empresa passou.
 *
 * @param baseSalary valor-base no período e na moeda informados
 * @param annualBonus bônus ou PLR esperado no ano, na mesma moeda
 * @param monthlyBenefits benefícios em reais por mês; vazio usa a premissa da configuração no CLT
 * @param respondBy até quando a empresa espera a resposta
 */
@Schema(name = "OfferCommand")
public record OfferCommand(@NotNull PayRegime regime, @NotNull @DecimalMin("0.01") BigDecimal baseSalary,
		@Nullable @Pattern(regexp = "[A-Za-z]{3}", message = "deve ser o código da moeda (BRL, USD, EUR)") String currency,
		@Nullable PayPeriod period, @Nullable @DecimalMin("0") BigDecimal annualBonus,
		@Nullable @DecimalMin("0") BigDecimal monthlyBenefits, @Nullable @Size(max = 2000) String benefitsNotes,
		@Nullable @Size(max = 2000) String equity, @Nullable WorkModel workModel, @Nullable LocalDate startDate,
		@Nullable LocalDate respondBy, @Nullable OfferDecision decision, @Nullable @Size(max = 5000) String notes) {
}
