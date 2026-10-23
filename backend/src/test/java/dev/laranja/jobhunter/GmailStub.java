package dev.laranja.jobhunter;

import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Base64;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import dev.laranja.jobhunter.shared.security.SecretCipher;

import org.springframework.jdbc.core.simple.JdbcClient;

import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;

/** Gmail conectado e mensagens simuladas no WireMock do Google, para os testes que leem e-mails. */
public final class GmailStub {

	private GmailStub() {
	}

	public static void connect(JdbcClient jdbc, SecretCipher cipher, WireMockServer google) {
		jdbc.sql("""
				insert into oauth_credential (provider, account_email, access_token_enc, access_token_expires_at,
				                              refresh_token_enc, scopes)
				values ('GOOGLE', 'eu@example.com', :access, :expires, :refresh, 'gmail.readonly')
				""")
			.param("access", cipher.encrypt("access-token"))
			.param("expires", Timestamp.from(Instant.now().plusSeconds(3600)))
			.param("refresh", cipher.encrypt("refresh-token"))
			.update();
	}

	/** Mensagem recebida (ainda não classificada) na tabela, com o corpo disponível no Gmail simulado. */
	public static long receive(JdbcClient jdbc, WireMockServer google, String gmailId, String sender, String subject,
			String body) {
		String data = Base64.getUrlEncoder().withoutPadding().encodeToString(("<p>" + body + "</p>").getBytes(StandardCharsets.UTF_8));
		google.stubFor(WireMock.get(urlPathEqualTo("/gmail/v1/users/me/messages/" + gmailId))
			.withQueryParam("format", equalTo("full"))
			.willReturn(okJson("""
					{"id": "%s", "payload": {"mimeType": "text/html", "body": {"data": "%s", "size": 10}}}
					""".formatted(gmailId, data))));
		return jdbc.sql("""
				insert into email_message (gmail_id, sender, subject, snippet, received_at, kind)
				values (:gmailId, :sender, :subject, :snippet, now(), 'OUTRO') returning id
				""")
			.param("gmailId", gmailId)
			.param("sender", sender)
			.param("subject", subject)
			.param("snippet", body)
			.query(Long.class)
			.single();
	}

}
