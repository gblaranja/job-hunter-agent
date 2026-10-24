package dev.laranja.jobhunter.notifications;

import org.jspecify.annotations.Nullable;

/**
 * Uma notificação foi gravada de verdade (não repetida pela {@code dedup_key}). Os canais externos, como o Telegram,
 * escutam este evento e enviam depois do commit.
 */
public record NotificationCreated(long id, String type, String title, @Nullable String body, @Nullable String link) {
}
