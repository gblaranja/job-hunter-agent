package dev.laranja.jobhunter.inbox;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import dev.laranja.jobhunter.inbox.internal.GmailAccess;
import dev.laranja.jobhunter.inbox.internal.GmailAccess.AlertBody;
import dev.laranja.jobhunter.inbox.internal.GmailAccess.Metadata;
import dev.laranja.jobhunter.shared.jdbc.JdbcRows;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

/**
 * Caixa de entrada da label de vagas no Gmail: sincroniza os e-mails novos, separa os alertas de vaga e entrega
 * o corpo deles para a coleta. Só a label configurada é lida; o corpo não é guardado no banco.
 */
@Service
public class Mailbox {

	private static final Logger log = LoggerFactory.getLogger(Mailbox.class);

	private final GmailAccess gmail;

	private final GmailProperties properties;

	private final JdbcClient jdbc;

	private final Clock clock;

	Mailbox(GmailAccess gmail, GmailProperties properties, JdbcClient jdbc, Clock clock) {
		this.gmail = gmail;
		this.properties = properties;
		this.jdbc = jdbc;
		this.clock = clock;
	}

	public boolean connected() {
		return this.gmail.connected();
	}

	public MailboxStatus status() {
		Instant lastSync = this.jdbc
			.sql("select last_full_sync_at from gmail_sync_state order by last_full_sync_at desc limit 1")
			.query((rs, row) -> JdbcRows.instant(rs, "last_full_sync_at"))
			.optional()
			.orElse(null);
		return new MailboxStatus(this.properties.configured(), this.gmail.connected(),
				this.gmail.accountEmail().orElse(null), this.properties.label(), lastSync);
	}

	/**
	 * Busca os e-mails novos da label. Na primeira vez lista os últimos dias; depois segue o histórico do Gmail a
	 * partir do último ponto, e volta à listagem completa se esse ponto tiver expirado.
	 * @return quantos e-mails novos foram registrados
	 */
	public SyncResult sync() {
		String labelId = this.gmail.labelId(this.properties.label())
			.orElseThrow(() -> new GmailNotConnectedException(
					"A label \"" + this.properties.label() + "\" não existe no Gmail. Crie a label e os filtros."));
		Optional<String> lastHistoryId = this.jdbc
			.sql("select last_history_id from gmail_sync_state where label_id = :labelId")
			.param("labelId", labelId)
			.query(BigDecimal.class)
			.optional()
			.map(BigDecimal::toPlainString);

		Set<String> messageIds = new LinkedHashSet<>();
		String newHistoryId;
		boolean full = lastHistoryId.isEmpty();
		if (!full) {
			Optional<GmailAccess.Changes> changes = this.gmail.changesSince(lastHistoryId.get(), labelId);
			if (changes.isPresent()) {
				messageIds.addAll(changes.get().messageIds());
				newHistoryId = changes.get().historyId();
			}
			else {
				log.info("Ponto de sincronização do Gmail expirou; refazendo a listagem completa.");
				full = true;
				newHistoryId = null;
			}
		}
		else {
			newHistoryId = null;
		}
		if (full) {
			newHistoryId = this.gmail.currentHistoryId();
			messageIds.addAll(this.gmail.recentMessageIds(labelId, this.properties.initialSyncDays()));
		}

		int created = 0;
		int alerts = 0;
		for (String messageId : messageIds) {
			if (alreadyKnown(messageId)) {
				continue;
			}
			Metadata metadata = this.gmail.metadata(messageId);
			boolean alert = isAlert(metadata.sender(), metadata.subject());
			created += this.jdbc.sql("""
					insert into email_message (gmail_id, thread_id, sender, subject, snippet, received_at, kind)
					values (:gmailId, :threadId, :sender, :subject, :snippet, :receivedAt, :kind)
					on conflict (gmail_id) do nothing
					""")
				.param("gmailId", metadata.id())
				.param("threadId", metadata.threadId())
				.param("sender", truncate(metadata.sender(), 320))
				.param("subject", truncate(metadata.subject(), 1000))
				.param("snippet", truncate(metadata.snippet(), 1000))
				.param("receivedAt", Timestamp.from(metadata.receivedAt()))
				.param("kind", alert ? "ALERTA" : "OUTRO")
				.update();
			alerts += alert ? 1 : 0;
		}
		saveSyncPoint(labelId, newHistoryId);
		return new SyncResult(created, alerts);
	}

	/** Alertas de vaga ainda não lidos pela coleta, com o HTML buscado no Gmail. */
	public List<AlertEmail> pendingAlerts(int limit) {
		record Pending(long id, String gmailId, String sender, @Nullable String subject, Instant receivedAt) {
		}
		List<Pending> pending = this.jdbc.sql("""
				select id, gmail_id, sender, subject, received_at from email_message
				where kind = 'ALERTA' and parsed_at is null
				order by received_at
				limit :limit
				""")
			.param("limit", limit)
			.query((rs, row) -> new Pending(rs.getLong("id"), rs.getString("gmail_id"), rs.getString("sender"),
					rs.getString("subject"), JdbcRows.instant(rs, "received_at")))
			.list();
		List<AlertEmail> alerts = new ArrayList<>();
		for (Pending email : pending) {
			Optional<AlertBody> body = this.gmail.body(email.gmailId());
			if (body.isEmpty()) {
				log.warn("Alerta {} sem corpo legível; marcado como lido.", email.gmailId());
				markParsed(email.id(), 0);
				continue;
			}
			alerts.add(new AlertEmail(email.id(), email.sender(), email.subject(), email.receivedAt(),
					body.get().html()));
		}
		return alerts;
	}

	public void markParsed(long emailId, int jobsFound) {
		this.jdbc.sql("update email_message set parsed_at = :now, jobs_found = :jobs where id = :id")
			.param("now", Timestamp.from(this.clock.instant()))
			.param("jobs", jobsFound)
			.param("id", emailId)
			.update();
	}

	private boolean isAlert(String sender, @Nullable String subject) {
		return this.properties.effectiveAlertRules().stream().anyMatch(rule -> rule.matches(sender, subject));
	}

	private boolean alreadyKnown(String gmailId) {
		return this.jdbc.sql("select count(*) from email_message where gmail_id = :gmailId")
			.param("gmailId", gmailId)
			.query(Long.class)
			.single() > 0;
	}

	private void saveSyncPoint(String labelId, @Nullable String historyId) {
		if (historyId == null) {
			return;
		}
		this.jdbc.sql("""
				insert into gmail_sync_state (label_id, last_history_id, last_full_sync_at)
				values (:labelId, :historyId, :now)
				on conflict (label_id) do update
				set last_history_id = excluded.last_history_id, last_full_sync_at = excluded.last_full_sync_at
				""")
			.param("labelId", labelId)
			.param("historyId", new BigDecimal(historyId))
			.param("now", Timestamp.from(this.clock.instant()))
			.update();
	}

	private static @Nullable String truncate(@Nullable String value, int max) {
		return (value == null || value.length() <= max) ? value : value.substring(0, max);
	}

	public record SyncResult(int newMessages, int newAlerts) {
	}

	public record AlertEmail(long emailId, String sender, @Nullable String subject, Instant receivedAt, String html) {
	}

	/**
	 * @param configured client id e secret do Google definidos no ambiente
	 * @param lastSyncAt última sincronização bem-sucedida
	 */
	public record MailboxStatus(boolean configured, boolean connected, @Nullable String accountEmail, String label,
			@Nullable Instant lastSyncAt) {
	}

}
