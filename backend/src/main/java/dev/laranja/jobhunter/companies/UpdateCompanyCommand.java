package dev.laranja.jobhunter.companies;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.jspecify.annotations.Nullable;

/**
 * Dados editáveis da empresa: status de monitoramento, site, ATS usado para a coleta e notas.
 *
 * @param careersUrl página de carreira; sem valor, fica a que já estava, e texto em branco apaga. Com ATS e slug em
 * branco, os dois são detectados por ela (Greenhouse, Lever, Ashby, SmartRecruiters, Workable, Teamtailor, Recruitee)
 */
public record UpdateCompanyCommand(@Nullable WatchStatus watchStatus, @Size(max = 500) @Nullable String website,
		@Size(max = 30) @Nullable String atsType, @Size(max = 200) @Nullable String atsSlug,
		@Nullable String notes,
		@Size(max = 500) @Pattern(regexp = "(https?://\\S+)?", message = "deve ser um link http(s)") @Nullable String careersUrl) {
}
