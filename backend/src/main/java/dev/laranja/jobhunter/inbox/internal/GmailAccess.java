package dev.laranja.jobhunter.inbox.internal;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import dev.laranja.jobhunter.inbox.internal.GmailClient.HistoryExpiredException;
import dev.laranja.jobhunter.inbox.internal.GmailDtos.History;
import dev.laranja.jobhunter.inbox.internal.GmailDtos.HistoryList;
import dev.laranja.jobhunter.inbox.internal.GmailDtos.Message;
import dev.laranja.jobhunter.inbox.internal.GmailDtos.MessageAdded;
import dev.laranja.jobhunter.inbox.internal.GmailDtos.MessageList;
import dev.laranja.jobhunter.inbox.internal.GmailDtos.MessageRef;
import org.jspecify.annotations.Nullable;

import org.springframework.stereotype.Component;

/** Fachada da Gmail API para o {@code Mailbox}: devolve só o que a sincronização precisa, já convertido. */
@Component
public class GmailAccess {

	private static final Pattern ADDRESS = Pattern.compile("<([^>]+)>");

	private final GmailClient client;

	private final GmailCredentials credentials;

	GmailAccess(GmailClient client, GmailCredentials credentials) {
		this.client = client;
		this.credentials = credentials;
	}

	public boolean connected() {
		return this.credentials.connected();
	}

	public Optional<String> accountEmail() {
		return this.credentials.accountEmail();
	}

	public Optional<String> labelId(String name) {
		return this.client.labels().stream().filter(label -> label.name().equalsIgnoreCase(name)).map(GmailDtos.Label::id).findFirst();
	}

	public String currentHistoryId() {
		return this.client.profile().historyId();
	}

	public List<String> recentMessageIds(String labelId, int days) {
		List<String> ids = new ArrayList<>();
		String pageToken = null;
		do {
			MessageList page = this.client.listMessages(labelId, "newer_than:" + days + "d", pageToken);
			if (page.messages() != null) {
				page.messages().stream().map(MessageRef::id).forEach(ids::add);
			}
			pageToken = page.nextPageToken();
		}
		while (pageToken != null);
		return ids;
	}

	/** Mensagens adicionadas à label desde o ponto informado; vazio se o ponto expirou. */
	public Optional<Changes> changesSince(String historyId, String labelId) {
		Set<String> ids = new LinkedHashSet<>();
		String latest = historyId;
		String pageToken = null;
		try {
			do {
				HistoryList page = this.client.history(historyId, labelId, pageToken);
				if (page.history() != null) {
					for (History history : page.history()) {
						if (history.messagesAdded() != null) {
							history.messagesAdded().stream().map(MessageAdded::message).map(MessageRef::id).forEach(ids::add);
						}
					}
				}
				latest = page.historyId();
				pageToken = page.nextPageToken();
			}
			while (pageToken != null);
		}
		catch (HistoryExpiredException ex) {
			return Optional.empty();
		}
		return Optional.of(new Changes(List.copyOf(ids), latest));
	}

	public Metadata metadata(String messageId) {
		Message message = this.client.metadata(messageId);
		String from = (message.payload() != null) ? message.payload().header("From") : null;
		String subject = (message.payload() != null) ? message.payload().header("Subject") : null;
		Instant receivedAt = (message.internalDate() != null) ? Instant.ofEpochMilli(Long.parseLong(message.internalDate()))
				: Instant.now();
		return new Metadata(message.id(), message.threadId(), senderAddress(from), subject, message.snippet(), receivedAt);
	}

	public Optional<AlertBody> body(String messageId) {
		Message message = this.client.full(messageId);
		return Optional.ofNullable(message.payload()).flatMap(MimeBodyExtractor::html).map(AlertBody::new);
	}

	/** "LinkedIn Job Alerts &lt;jobalerts-noreply@linkedin.com&gt;" vira só o endereço. */
	static String senderAddress(@Nullable String from) {
		if (from == null) {
			return "";
		}
		Matcher matcher = ADDRESS.matcher(from);
		return (matcher.find() ? matcher.group(1) : from).strip().toLowerCase(Locale.ROOT);
	}

	public record Changes(List<String> messageIds, String historyId) {
	}

	public record Metadata(String id, @Nullable String threadId, String sender, @Nullable String subject,
			@Nullable String snippet, Instant receivedAt) {
	}

	public record AlertBody(String html) {
	}

}
