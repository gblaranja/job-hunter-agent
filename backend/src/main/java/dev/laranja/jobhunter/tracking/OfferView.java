package dev.laranja.jobhunter.tracking;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import dev.laranja.jobhunter.jobs.WorkModel;
import dev.laranja.jobhunter.shared.money.PayPeriod;
import dev.laranja.jobhunter.shared.money.PayRegime;
import io.swagger.v3.oas.annotations.media.Schema;
import org.jspecify.annotations.Nullable;

/**
 * Uma proposta com o equivalente anual em reais, para comparar lado a lado.
 *
 * @param annualEquivalent CLT: 13,33 salários, FGTS, benefícios e bônus; PJ: líquido de impostos e custos; nulo
 * quando a moeda é estrangeira e a PTAX não respondeu
 * @param exchangeRate câmbio usado na conversão; nulo em reais
 */
@Schema(name = "OfferView")
public record OfferView(long id, long applicationId, long jobId, String title, String company, PayRegime regime,
		BigDecimal baseSalary, String currency, PayPeriod period, @Nullable BigDecimal annualBonus,
		@Nullable BigDecimal monthlyBenefits, @Nullable String benefitsNotes, @Nullable String equity,
		@Nullable WorkModel workModel, @Nullable LocalDate startDate, @Nullable LocalDate respondBy,
		OfferDecision decision, @Nullable Instant decidedAt, @Nullable String notes,
		@Nullable BigDecimal annualEquivalent, @Nullable BigDecimal monthlyEquivalent, @Nullable BigDecimal exchangeRate,
		Instant createdAt) {
}
