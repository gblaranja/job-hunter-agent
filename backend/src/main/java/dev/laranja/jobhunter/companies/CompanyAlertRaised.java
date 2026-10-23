package dev.laranja.jobhunter.companies;

import org.jspecify.annotations.Nullable;

/**
 * Um sinal sobre uma empresa merece sua atenção (empresa alvo abriu vaga, voltou a contratar, volume caiu).
 *
 * @param dedupKey identifica o alerta, para não repetir a mesma notificação
 */
public record CompanyAlertRaised(long companyId, String type, String title, @Nullable String body, String dedupKey) {
}
