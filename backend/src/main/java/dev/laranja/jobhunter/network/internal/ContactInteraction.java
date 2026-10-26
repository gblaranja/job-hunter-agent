package dev.laranja.jobhunter.network.internal;

import java.time.Instant;

import dev.laranja.jobhunter.network.InteractionChannel;
import dev.laranja.jobhunter.network.InteractionDirection;
import dev.laranja.jobhunter.network.InteractionKind;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.jspecify.annotations.Nullable;

/** Uma conversa com um contato, ligada ou não a uma candidatura. */
@Entity
@Table(name = "contact_interaction")
public class ContactInteraction {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	private long contactId;

	private Long applicationId;

	@Enumerated(EnumType.STRING)
	private InteractionChannel channel;

	@Enumerated(EnumType.STRING)
	private InteractionDirection direction;

	@Enumerated(EnumType.STRING)
	private InteractionKind kind;

	private String summary;

	private Long emailMessageId;

	private Instant occurredAt;

	private Instant createdAt;

	protected ContactInteraction() {
	}

	public static ContactInteraction record(long contactId, @Nullable Long applicationId, InteractionChannel channel,
			InteractionDirection direction, InteractionKind kind, @Nullable String summary,
			@Nullable Long emailMessageId, Instant occurredAt, Instant now) {
		ContactInteraction interaction = new ContactInteraction();
		interaction.contactId = contactId;
		interaction.applicationId = applicationId;
		interaction.channel = channel;
		interaction.direction = direction;
		interaction.kind = kind;
		interaction.summary = (summary == null || summary.isBlank()) ? null : summary.strip();
		interaction.emailMessageId = emailMessageId;
		interaction.occurredAt = occurredAt;
		interaction.createdAt = now;
		return interaction;
	}

	public Long getId() {
		return this.id;
	}

}
