package dev.laranja.jobhunter.recognition;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import org.jspecify.annotations.Nullable;

/**
 * Um reconhecimento da empresa, com a evidência.
 *
 * @param level nível na data de hoje: um selo com a validade vencida aparece como EXPIRADA
 * @param reviewStatus AUTOMATICO e CONFIRMADO aparecem como selo; PENDENTE está na fila de revisão
 * @param homonyms empresas cadastradas com nome parecido (só na fila de revisão)
 */
public record RecognitionView(long id, long companyId, String companyName, String program, RecognitionLevel level,
		@Nullable String rankingName, @Nullable Integer rankingPosition, @Nullable Integer year,
		@Nullable LocalDate validUntil, @Nullable String evidenceUrl, @Nullable String evidenceExcerpt,
		@Nullable String rationale, @Nullable String matchedName, double confidence, String verifiedBy,
		String reviewStatus, String source, Instant checkedAt, List<String> homonyms) {
}
