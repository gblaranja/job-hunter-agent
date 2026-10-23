package dev.laranja.jobhunter.inbox;

import dev.laranja.jobhunter.shared.BusinessRuleException;

/** O Gmail não está conectado, ou a autorização expirou e precisa ser refeita. */
public class GmailNotConnectedException extends BusinessRuleException {

	public GmailNotConnectedException(String message) {
		super(message);
	}

}
