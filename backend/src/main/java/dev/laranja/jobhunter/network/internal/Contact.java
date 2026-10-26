package dev.laranja.jobhunter.network.internal;

import java.time.Instant;
import java.time.LocalDate;

import dev.laranja.jobhunter.network.ContactRole;
import dev.laranja.jobhunter.network.ContactSource;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.jspecify.annotations.Nullable;

/** Uma pessoa da sua rede ligada à busca: recrutador, gestor, quem pode indicar ou uma conexão do LinkedIn. */
@Entity
@Table(name = "contact")
public class Contact {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	private String name;

	@Enumerated(EnumType.STRING)
	private ContactRole role;

	private Long companyId;

	private String companyName;

	private String position;

	private String linkedinUrl;

	private String email;

	private String notes;

	@Enumerated(EnumType.STRING)
	private ContactSource source;

	private LocalDate connectedOn;

	private Instant createdAt;

	private Instant updatedAt;

	protected Contact() {
	}

	public static Contact create(String name, ContactRole role, ContactSource source, @Nullable Long companyId,
			@Nullable String companyName, @Nullable String position, @Nullable String linkedinUrl,
			@Nullable String email, @Nullable String notes, @Nullable LocalDate connectedOn, Instant now) {
		Contact contact = new Contact();
		contact.source = source;
		contact.createdAt = now;
		contact.edit(name, role, companyId, companyName, position, linkedinUrl, email, notes, now);
		contact.connectedOn = connectedOn;
		return contact;
	}

	public void edit(String name, ContactRole role, @Nullable Long companyId, @Nullable String companyName,
			@Nullable String position, @Nullable String linkedinUrl, @Nullable String email, @Nullable String notes,
			Instant now) {
		this.name = name.strip();
		this.role = role;
		this.companyId = companyId;
		this.companyName = blankToNull(companyName);
		this.position = blankToNull(position);
		this.linkedinUrl = blankToNull(linkedinUrl);
		this.email = blankToNull(email);
		this.notes = blankToNull(notes);
		this.updatedAt = now;
	}

	/**
	 * Reimportação do export do LinkedIn: atualiza empresa, cargo e data da conexão, sem apagar o que você anotou
	 * nem rebaixar o papel que você deu à pessoa.
	 */
	public void refreshConnection(String name, @Nullable Long companyId, @Nullable String companyName,
			@Nullable String position, @Nullable String email, @Nullable LocalDate connectedOn, Instant now) {
		this.name = name.strip();
		this.companyId = companyId;
		this.companyName = blankToNull(companyName);
		this.position = blankToNull(position);
		if (this.email == null) {
			this.email = blankToNull(email);
		}
		if (connectedOn != null) {
			this.connectedOn = connectedOn;
		}
		this.updatedAt = now;
	}

	/** Um recrutador que te procurou de novo: completa o que faltava, sem sobrescrever o que você editou. */
	public void completeFrom(@Nullable Long companyId, @Nullable String companyName, @Nullable String email, Instant now) {
		if (this.companyId == null && companyId != null) {
			this.companyId = companyId;
		}
		if (this.companyName == null) {
			this.companyName = blankToNull(companyName);
		}
		if (this.email == null) {
			this.email = blankToNull(email);
		}
		this.updatedAt = now;
	}

	private static @Nullable String blankToNull(@Nullable String value) {
		return (value == null || value.isBlank()) ? null : value.strip();
	}

	public Long getId() {
		return this.id;
	}

	public String getName() {
		return this.name;
	}

	public ContactRole getRole() {
		return this.role;
	}

	public @Nullable Long getCompanyId() {
		return this.companyId;
	}

}
