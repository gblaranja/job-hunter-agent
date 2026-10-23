package dev.laranja.jobhunter.inbox.internal;

import org.springframework.stereotype.Component;

/** Conectar e desconectar o Gmail: guarda os tokens e lembra qual conta foi autorizada. */
@Component
public class GmailConnection {

	private final GmailCredentials credentials;

	private final GmailClient client;

	GmailConnection(GmailCredentials credentials, GmailClient client) {
		this.credentials = credentials;
		this.client = client;
	}

	public String authorizationUrl(String state) {
		return this.credentials.authorizationUrl(state);
	}

	public void complete(String code) {
		this.credentials.completeAuthorization(code);
		this.credentials.rememberAccount(this.client.profile().emailAddress());
	}

	public void disconnect() {
		this.credentials.disconnect();
	}

}
