package dev.laranja.jobhunter.tracking.internal;

import java.time.Instant;

import dev.laranja.jobhunter.tracking.ApplicationStatus;
import dev.laranja.jobhunter.tracking.EventOrigin;
import dev.laranja.jobhunter.tracking.ResponseType;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;

/** Linha do histórico do funil. É append-only: o banco recusa update e delete. */
@Entity
@Immutable
@Table(name = "application_event")
public class ApplicationEvent {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	private long applicationId;

	@Enumerated(EnumType.STRING)
	private ApplicationStatus fromStatus;

	@Enumerated(EnumType.STRING)
	private ApplicationStatus toStatus;

	@Enumerated(EnumType.STRING)
	private ResponseType responseType;

	@Enumerated(EnumType.STRING)
	private EventOrigin origin;

	private Long emailMessageId;

	private String note;

	private Instant createdAt;

	protected ApplicationEvent() {
	}

	static ApplicationEvent statusChange(long applicationId, ApplicationStatus from, ApplicationStatus to,
			EventOrigin origin, String note, Instant now) {
		ApplicationEvent event = new ApplicationEvent();
		event.applicationId = applicationId;
		event.fromStatus = from;
		event.toStatus = to;
		event.origin = origin;
		event.note = note;
		event.createdAt = now;
		return event;
	}

	public ApplicationStatus getFromStatus() {
		return this.fromStatus;
	}

	public ApplicationStatus getToStatus() {
		return this.toStatus;
	}

	public String getNote() {
		return this.note;
	}

}
