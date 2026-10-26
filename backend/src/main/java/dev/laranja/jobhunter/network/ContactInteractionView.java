package dev.laranja.jobhunter.network;

import java.time.Instant;

import org.jspecify.annotations.Nullable;

/** Conversa com um contato, para o histórico do contato e a linha do tempo da candidatura. */
public record ContactInteractionView(long id, long contactId, String contactName, ContactRole contactRole,
		@Nullable Long applicationId, InteractionChannel channel, InteractionDirection direction, InteractionKind kind,
		@Nullable String summary, Instant occurredAt) {
}
