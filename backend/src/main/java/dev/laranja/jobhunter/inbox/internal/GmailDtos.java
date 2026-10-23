package dev.laranja.jobhunter.inbox.internal;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.jspecify.annotations.Nullable;

/** Recortes das respostas da Gmail API e do endpoint de token do Google usados pela aplicação. */
final class GmailDtos {

	private GmailDtos() {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record TokenResponse(@JsonProperty("access_token") String accessToken, @JsonProperty("expires_in") long expiresIn,
			@JsonProperty("refresh_token") @Nullable String refreshToken, @Nullable String scope) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record Profile(String emailAddress, String historyId) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record Labels(@Nullable List<Label> labels) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record Label(String id, String name) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record MessageList(@Nullable List<MessageRef> messages, @Nullable String nextPageToken) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record MessageRef(String id, @Nullable String threadId) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record Message(String id, @Nullable String threadId, @Nullable List<String> labelIds, @Nullable String snippet,
			@Nullable String historyId, @Nullable String internalDate, @Nullable MessagePart payload) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record MessagePart(@Nullable String mimeType, @Nullable List<Header> headers, @Nullable Body body,
			@Nullable List<MessagePart> parts) {

		@Nullable String header(String name) {
			if (this.headers == null) {
				return null;
			}
			return this.headers.stream()
				.filter(header -> header.name().equalsIgnoreCase(name))
				.map(Header::value)
				.findFirst()
				.orElse(null);
		}

	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record Header(String name, String value) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record Body(@Nullable String data, long size) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record HistoryList(@Nullable List<History> history, String historyId, @Nullable String nextPageToken) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record History(@Nullable List<MessageAdded> messagesAdded) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record MessageAdded(MessageRef message) {
	}

}
