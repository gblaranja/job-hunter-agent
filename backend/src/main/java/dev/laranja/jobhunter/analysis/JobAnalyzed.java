package dev.laranja.jobhunter.analysis;

import org.jspecify.annotations.Nullable;

/**
 * A vaga foi analisada. {@code matchScore} é nulo enquanto não houver currículo padrão. Notificações de vagas com
 * score alto escutam este evento.
 */
public record JobAnalyzed(long jobId, @Nullable Integer matchScore) {
}
