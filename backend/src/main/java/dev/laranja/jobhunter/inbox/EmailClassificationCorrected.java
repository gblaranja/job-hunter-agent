package dev.laranja.jobhunter.inbox;

import org.jspecify.annotations.Nullable;

/** Você corrigiu a classificação (ou a candidatura ligada) de um e-mail; o tracking ajusta o funil. */
public record EmailClassificationCorrected(long emailId, EmailClassification classification,
		@Nullable Long applicationId) {
}
