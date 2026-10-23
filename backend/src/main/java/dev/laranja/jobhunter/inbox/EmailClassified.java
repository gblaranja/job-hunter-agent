package dev.laranja.jobhunter.inbox;

import java.time.Instant;

import org.jspecify.annotations.Nullable;

/**
 * Um retorno foi classificado. O tracking procura a candidatura correspondente e, com confiança suficiente, move o
 * card.
 *
 * @param companyGuess empresa citada no e-mail, segundo o classificador
 */
public record EmailClassified(long emailId, EmailClassification classification, double confidence,
		@Nullable String companyGuess, String sender, @Nullable String subject, Instant receivedAt) {
}
