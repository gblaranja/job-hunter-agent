package dev.laranja.jobhunter.companies.internal;

import java.time.Instant;

import dev.laranja.jobhunter.companies.CompanyKind;
import dev.laranja.jobhunter.companies.WatchStatus;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "company")
public class Company {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	private String name;

	private String normalizedName;

	private String website;

	private String atsType;

	private String atsSlug;

	@Enumerated(EnumType.STRING)
	private CompanyKind kind;

	@Enumerated(EnumType.STRING)
	private WatchStatus watchStatus;

	private String notes;

	private Instant firstSeenAt;

	protected Company() {
	}

	public static Company discovered(String name, String normalizedName, Instant now) {
		Company company = new Company();
		company.name = name.strip();
		company.normalizedName = normalizedName;
		company.kind = CompanyKind.CONTRATANTE;
		company.watchStatus = WatchStatus.DESCOBERTA;
		company.firstSeenAt = now;
		return company;
	}

	public void update(WatchStatus watchStatus, String website, String atsType, String atsSlug, String notes) {
		if (watchStatus != null) {
			this.watchStatus = watchStatus;
		}
		this.website = blankToNull(website);
		this.atsType = blankToNull(atsType);
		this.atsSlug = blankToNull(atsSlug);
		this.notes = blankToNull(notes);
	}

	public void changeWatchStatus(WatchStatus watchStatus) {
		this.watchStatus = watchStatus;
	}

	public Long getId() {
		return this.id;
	}

	public String getName() {
		return this.name;
	}

	public String getNormalizedName() {
		return this.normalizedName;
	}

	private static String blankToNull(String value) {
		return (value == null || value.isBlank()) ? null : value.strip();
	}

}
