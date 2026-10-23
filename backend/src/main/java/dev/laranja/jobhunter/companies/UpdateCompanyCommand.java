package dev.laranja.jobhunter.companies;

import jakarta.validation.constraints.Size;
import org.jspecify.annotations.Nullable;

/** Dados editáveis da empresa: status de monitoramento, site, ATS usado para a coleta e notas. */
public record UpdateCompanyCommand(@Nullable WatchStatus watchStatus, @Size(max = 500) @Nullable String website,
		@Size(max = 30) @Nullable String atsType, @Size(max = 200) @Nullable String atsSlug,
		@Nullable String notes) {
}
