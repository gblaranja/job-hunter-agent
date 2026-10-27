package dev.laranja.jobhunter.inbox;

import java.time.Instant;

import org.jspecify.annotations.Nullable;

/**
 * Um e-mail que pede ação foi classificado: um retorno (o tracking procura a candidatura e, com confiança suficiente,
 * move o card), uma confirmação de candidatura (o tracking registra ou confirma a aplicação) ou a abordagem de um
 * recrutador (o network cria o contato e a oportunidade).
 *
 * @param companyGuess empresa citada no e-mail, segundo o classificador
 * @param jobGuess cargo citado no e-mail
 * @param recruiterName quem escreveu, quando o classificador identificou
 * @param agency agência de recrutamento, quando diferente da empresa cliente
 */
public record EmailClassified(long emailId, EmailClassification classification, double confidence,
		@Nullable String companyGuess, String sender, @Nullable String subject, Instant receivedAt,
		@Nullable String jobGuess, @Nullable String recruiterName, @Nullable String agency) {

	public EmailClassified(long emailId, EmailClassification classification, double confidence,
			@Nullable String companyGuess, String sender, @Nullable String subject, Instant receivedAt) {
		this(emailId, classification, confidence, companyGuess, sender, subject, receivedAt, null, null, null);
	}

}
