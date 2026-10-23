package dev.laranja.jobhunter.inbox.internal;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Optional;

import dev.laranja.jobhunter.inbox.internal.GmailDtos.MessagePart;
import org.jsoup.nodes.Entities;

/** Extrai o corpo HTML de uma mensagem MIME da Gmail API; sem HTML, usa o texto puro. */
final class MimeBodyExtractor {

	private MimeBodyExtractor() {
	}

	static Optional<String> html(MessagePart payload) {
		return find(payload, "text/html").or(() -> find(payload, "text/plain")
			.map(text -> "<pre>" + Entities.escape(text) + "</pre>"));
	}

	private static Optional<String> find(MessagePart part, String mimeType) {
		if (part == null) {
			return Optional.empty();
		}
		if (mimeType.equalsIgnoreCase(part.mimeType()) && part.body() != null && part.body().data() != null) {
			return Optional.of(decode(part.body().data()));
		}
		if (part.parts() != null) {
			for (MessagePart child : part.parts()) {
				Optional<String> found = find(child, mimeType);
				if (found.isPresent()) {
					return found;
				}
			}
		}
		return Optional.empty();
	}

	// A Gmail API devolve o corpo em base64url; o charset do e-mail já vem convertido para UTF-8.
	private static String decode(String data) {
		return new String(Base64.getUrlDecoder().decode(data), StandardCharsets.UTF_8);
	}

}
