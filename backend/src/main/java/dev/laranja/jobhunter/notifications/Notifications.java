package dev.laranja.jobhunter.notifications;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.List;

import dev.laranja.jobhunter.shared.jdbc.JdbcRows;
import org.jspecify.annotations.Nullable;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Notificações dentro do backoffice (o sino); um canal como Telegram pode ser plugado aqui depois. */
@Service
@Transactional
public class Notifications {

	private final JdbcClient jdbc;

	private final Clock clock;

	Notifications(JdbcClient jdbc, Clock clock) {
		this.jdbc = jdbc;
		this.clock = clock;
	}

	/** Cria a notificação; com a mesma {@code dedupKey}, a segunda é ignorada. */
	public void notify(String type, String title, @Nullable String body, @Nullable String link, @Nullable String dedupKey) {
		this.jdbc.sql("""
				insert into notification (type, title, body, link, dedup_key, created_at)
				values (:type, :title, :body, :link, :dedupKey, :now)
				on conflict (dedup_key) do nothing
				""")
			.param("type", type)
			.param("title", title.length() > 300 ? title.substring(0, 300) : title)
			.param("body", body)
			.param("link", link)
			.param("dedupKey", dedupKey)
			.param("now", Timestamp.from(this.clock.instant()))
			.update();
	}

	@Transactional(readOnly = true)
	public List<NotificationView> recent() {
		return this.jdbc.sql("select * from notification order by created_at desc limit 50")
			.query((rs, row) -> new NotificationView(rs.getLong("id"), rs.getString("type"), rs.getString("title"),
					rs.getString("body"), rs.getString("link"), JdbcRows.instant(rs, "read_at"),
					JdbcRows.instant(rs, "created_at")))
			.list();
	}

	public void markRead(long id) {
		this.jdbc.sql("update notification set read_at = :now where id = :id and read_at is null")
			.param("now", Timestamp.from(this.clock.instant()))
			.param("id", id)
			.update();
	}

	public void markAllRead() {
		this.jdbc.sql("update notification set read_at = :now where read_at is null")
			.param("now", Timestamp.from(this.clock.instant()))
			.update();
	}

	public record NotificationView(long id, String type, String title, @Nullable String body, @Nullable String link,
			@Nullable Instant readAt, Instant createdAt) {
	}

}
