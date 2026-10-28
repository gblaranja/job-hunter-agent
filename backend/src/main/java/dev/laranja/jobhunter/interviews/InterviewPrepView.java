package dev.laranja.jobhunter.interviews;

import java.time.Instant;

import dev.laranja.jobhunter.tracking.ApplicationStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import org.jspecify.annotations.Nullable;

/**
 * Preparação de uma candidatura para uma etapa.
 *
 * @param status GERANDO, PRONTO ou FALHOU
 * @param source IA (Claude API) ou REGRAS (sem a chave, ou depois de uma falha da API, registrada em
 * {@code lastError})
 */
@Schema(name = "InterviewPrepView")
public record InterviewPrepView(long id, long applicationId, long jobId, ApplicationStatus stage, String status,
		@Nullable InterviewPrepContent content, @Nullable String source, @Nullable String modelVersion,
		@Nullable String lastError, Instant createdAt, Instant updatedAt) {
}
