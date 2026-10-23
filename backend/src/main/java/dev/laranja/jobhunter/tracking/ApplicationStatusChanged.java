package dev.laranja.jobhunter.tracking;

import java.time.Instant;

/**
 * A candidatura mudou de etapa (ou foi criada, com {@code from} nulo). Métricas, alertas e a preparação para
 * entrevista escutam este evento.
 */
public record ApplicationStatusChanged(long applicationId, long jobId, ApplicationStatus from, ApplicationStatus to,
		EventOrigin origin, Instant occurredAt) {
}
