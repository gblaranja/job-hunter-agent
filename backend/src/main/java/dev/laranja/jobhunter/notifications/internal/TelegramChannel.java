package dev.laranja.jobhunter.notifications.internal;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Envia mensagens pelo bot do Telegram ({@code sendMessage} da Bot API). Os erros saem sem o token: a URL da Bot API
 * carrega o token e as exceções do {@link RestClient} repetem a URL.
 */
@Component
@EnableConfigurationProperties(NotificationProperties.class)
public class TelegramChannel {

	/** Limite de caracteres de uma mensagem do Telegram. */
	static final int MAX_LENGTH = 4096;

	private final NotificationProperties properties;

	private final RestClient http;

	TelegramChannel(NotificationProperties properties, RestClient.Builder http) {
		this.properties = properties;
		this.http = http.clone().build();
	}

	public boolean enabled() {
		return this.properties.telegram().enabled();
	}

	/** Se o canal está configurado e quais tipos de notificação vão ao celular. */
	public TelegramStatus status() {
		return new TelegramStatus(enabled(), this.properties.telegram().types());
	}

	/** Mensagem de teste, para conferir o token e o chat id pelas Configurações. */
	public void sendTest() {
		send("Teste do Job Hunter Agent", "Se você recebeu esta mensagem, os alertas vão chegar aqui.", "/today");
	}

	boolean sendsType(String type) {
		return enabled() && this.properties.telegram().types().contains(type);
	}

	/** Mensagem com título em negrito, corpo e o link para o backoffice. */
	void send(String title, @Nullable String body, @Nullable String link) {
		StringBuilder text = new StringBuilder("<b>").append(escape(title)).append("</b>");
		if (body != null && !body.isBlank()) {
			text.append('\n').append(escape(body.strip()));
		}
		if (link != null && !link.isBlank()) {
			text.append("\n\n<a href=\"").append(escape(absolute(link))).append("\">Abrir no Job Hunter</a>");
		}
		sendHtml(text.toString());
	}

	/** Envia um texto já em HTML do Telegram (só {@code b}, {@code i} e {@code a}). */
	void sendHtml(String html) {
		NotificationProperties.Telegram config = this.properties.telegram();
		if (!config.enabled()) {
			throw new TelegramException("O Telegram não está configurado (token do bot e chat id).");
		}
		Map<String, Object> payload = new LinkedHashMap<>();
		payload.put("chat_id", config.chatId());
		payload.put("text", (html.length() <= MAX_LENGTH) ? html : html.substring(0, MAX_LENGTH - 1) + "…");
		payload.put("parse_mode", "HTML");
		payload.put("link_preview_options", Map.of("is_disabled", true));
		try {
			this.http.post()
				// sem template: o ":" do token não pode virar %3A
				.uri(URI.create(config.baseUrl().replaceAll("/+$", "") + "/bot" + config.botToken().strip()
						+ "/sendMessage"))
				.contentType(MediaType.APPLICATION_JSON)
				.body(payload)
				.retrieve()
				.toBodilessEntity();
		}
		catch (RestClientException | IllegalArgumentException ex) {
			throw new TelegramException("Falha ao enviar ao Telegram: " + hideToken(ex.getMessage()));
		}
	}

	String absolute(String link) {
		if (link.startsWith("http://") || link.startsWith("https://")) {
			return link;
		}
		String base = this.properties.publicUrl().replaceAll("/+$", "");
		return base + (link.startsWith("/") ? link : "/" + link);
	}

	private String hideToken(@Nullable String message) {
		String token = this.properties.telegram().botToken();
		if (message == null) {
			return "";
		}
		if (token == null || token.isBlank()) {
			return message;
		}
		return message.replace(token, "***").replace(URLEncoder.encode(token, StandardCharsets.UTF_8), "***");
	}

	static String escape(String text) {
		return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
	}

	/**
	 * @param configured token do bot e chat id preenchidos
	 * @param types tipos de notificação que vão ao celular
	 */
	public record TelegramStatus(boolean configured, List<String> types) {
	}

	/** Falha no envio, já sem o token na mensagem. */
	public static class TelegramException extends RuntimeException {

		TelegramException(String message) {
			super(message);
		}

	}

}
