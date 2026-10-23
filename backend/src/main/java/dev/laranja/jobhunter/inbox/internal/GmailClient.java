package dev.laranja.jobhunter.inbox.internal;

import java.util.List;
import java.util.Optional;

import dev.laranja.jobhunter.inbox.GmailProperties;
import dev.laranja.jobhunter.inbox.internal.GmailDtos.HistoryList;
import dev.laranja.jobhunter.inbox.internal.GmailDtos.Label;
import dev.laranja.jobhunter.inbox.internal.GmailDtos.Labels;
import dev.laranja.jobhunter.inbox.internal.GmailDtos.Message;
import dev.laranja.jobhunter.inbox.internal.GmailDtos.MessageList;
import dev.laranja.jobhunter.inbox.internal.GmailDtos.Profile;
import org.jspecify.annotations.Nullable;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

/** Chamadas de leitura à Gmail API (escopo gmail.readonly), autenticadas com o access token atual. */
@Component
public class GmailClient {

	private final RestClient http;

	private final GmailCredentials credentials;

	GmailClient(GmailProperties properties, GmailCredentials credentials, RestClient.Builder http) {
		this.http = http.baseUrl(properties.apiBaseUrl().toString() + "/gmail/v1/users/me").build();
		this.credentials = credentials;
	}

	Profile profile() {
		return get("/profile", Profile.class);
	}

	List<Label> labels() {
		Labels labels = get("/labels", Labels.class);
		return (labels.labels() != null) ? labels.labels() : List.of();
	}

	MessageList listMessages(String labelId, String query, @Nullable String pageToken) {
		return this.http.get()
			.uri(builder -> builder.path("/messages")
				.queryParam("labelIds", labelId)
				.queryParam("q", query)
				.queryParam("maxResults", 100)
				.queryParamIfPresent("pageToken", Optional.ofNullable(pageToken))
				.build())
			.headers(headers -> headers.setBearerAuth(this.credentials.accessToken()))
			.retrieve()
			.body(MessageList.class);
	}

	Message metadata(String messageId) {
		return this.http.get()
			.uri(builder -> builder.path("/messages/{id}")
				.queryParam("format", "metadata")
				.queryParam("metadataHeaders", "From", "Subject")
				.build(messageId))
			.headers(headers -> headers.setBearerAuth(this.credentials.accessToken()))
			.retrieve()
			.body(Message.class);
	}

	public Message full(String messageId) {
		return this.http.get()
			.uri(builder -> builder.path("/messages/{id}").queryParam("format", "full").build(messageId))
			.headers(headers -> headers.setBearerAuth(this.credentials.accessToken()))
			.retrieve()
			.body(Message.class);
	}

	/**
	 * Mensagens adicionadas desde {@code startHistoryId}.
	 * @throws HistoryExpiredException quando o Gmail não guarda mais esse ponto do histórico (404)
	 */
	HistoryList history(String startHistoryId, String labelId, @Nullable String pageToken) {
		try {
			return this.http.get()
				.uri(builder -> builder.path("/history")
					.queryParam("startHistoryId", startHistoryId)
					.queryParam("labelId", labelId)
					.queryParam("historyTypes", "messageAdded")
					.queryParamIfPresent("pageToken", Optional.ofNullable(pageToken))
					.build())
				.headers(headers -> headers.setBearerAuth(this.credentials.accessToken()))
				.retrieve()
				.body(HistoryList.class);
		}
		catch (HttpClientErrorException ex) {
			if (ex.getStatusCode() == HttpStatus.NOT_FOUND) {
				throw new HistoryExpiredException();
			}
			throw ex;
		}
	}

	private <T> T get(String path, Class<T> type) {
		return this.http.get()
			.uri(path)
			.headers(headers -> headers.setBearerAuth(this.credentials.accessToken()))
			.retrieve()
			.body(type);
	}

	static class HistoryExpiredException extends RuntimeException {

		HistoryExpiredException() {
			super("O ponto de sincronização do Gmail expirou.");
		}

	}

}
