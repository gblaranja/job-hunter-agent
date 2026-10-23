package dev.laranja.jobhunter.inbox.web;

import java.net.URI;
import java.util.UUID;

import dev.laranja.jobhunter.inbox.GmailNotConnectedException;
import dev.laranja.jobhunter.inbox.GmailProperties;
import dev.laranja.jobhunter.inbox.Mailbox;
import dev.laranja.jobhunter.inbox.Mailbox.MailboxStatus;
import dev.laranja.jobhunter.inbox.internal.GmailConnection;
import jakarta.servlet.http.HttpSession;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

/** Conexão com o Gmail: o navegador vai ao Google e volta no callback, já com a sessão do backoffice. */
@RestController
@RequestMapping("/api/admin/gmail")
class GmailController {

	private static final Logger log = LoggerFactory.getLogger(GmailController.class);

	private static final String STATE_ATTRIBUTE = GmailController.class.getName() + ".state";

	private final Mailbox mailbox;

	private final GmailConnection connection;

	private final GmailProperties properties;

	GmailController(Mailbox mailbox, GmailConnection connection, GmailProperties properties) {
		this.mailbox = mailbox;
		this.connection = connection;
		this.properties = properties;
	}

	@GetMapping("/status")
	MailboxStatus status() {
		return this.mailbox.status();
	}

	/** Redireciona para o consentimento do Google; o state protege o callback contra CSRF. */
	@GetMapping("/connect")
	ResponseEntity<Void> connect(HttpSession session) {
		String state = UUID.randomUUID().toString();
		session.setAttribute(STATE_ATTRIBUTE, state);
		return redirect(URI.create(this.connection.authorizationUrl(state)));
	}

	@GetMapping("/callback")
	ResponseEntity<Void> callback(@RequestParam(required = false) @Nullable String code,
			@RequestParam(required = false) @Nullable String state,
			@RequestParam(required = false) @Nullable String error, HttpSession session) {
		Object expected = session.getAttribute(STATE_ATTRIBUTE);
		session.removeAttribute(STATE_ATTRIBUTE);
		if (error != null || code == null || state == null || !state.equals(expected)) {
			log.warn("Conexão com o Gmail cancelada ou inválida (error={}).", error);
			return redirect(afterConnect("erro"));
		}
		try {
			this.connection.complete(code);
			return redirect(afterConnect("conectado"));
		}
		catch (GmailNotConnectedException ex) {
			log.warn("Falha ao concluir a conexão com o Gmail: {}", ex.getMessage());
			return redirect(afterConnect("erro"));
		}
	}

	@DeleteMapping
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void disconnect() {
		this.connection.disconnect();
	}

	private URI afterConnect(String result) {
		return UriComponentsBuilder.fromUriString(this.properties.afterConnectUrl())
			.queryParam("gmail", result)
			.build()
			.toUri();
	}

	private static ResponseEntity<Void> redirect(URI location) {
		return ResponseEntity.status(HttpStatus.FOUND).location(location).build();
	}

}
