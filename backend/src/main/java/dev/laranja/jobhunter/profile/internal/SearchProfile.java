package dev.laranja.jobhunter.profile.internal;

import java.time.Instant;
import java.util.List;

import dev.laranja.jobhunter.profile.SearchProfileCommand;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "search_profile")
public class SearchProfile {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	private String name;

	@JdbcTypeCode(SqlTypes.ARRAY)
	private List<String> keywords;

	@JdbcTypeCode(SqlTypes.ARRAY)
	private List<String> excludedKeywords;

	@JdbcTypeCode(SqlTypes.ARRAY)
	private List<String> locations;

	private boolean remoteOnly;

	private boolean active;

	private Instant createdAt;

	protected SearchProfile() {
	}

	public static SearchProfile create(SearchProfileCommand command, Instant now) {
		SearchProfile profile = new SearchProfile();
		profile.createdAt = now;
		profile.apply(command);
		return profile;
	}

	public void apply(SearchProfileCommand command) {
		this.name = command.name().strip();
		this.keywords = clean(command.keywords());
		this.excludedKeywords = clean(command.excludedKeywords());
		this.locations = clean(command.locations());
		this.remoteOnly = command.remoteOnly();
		this.active = command.active();
	}

	private static List<String> clean(List<String> values) {
		return (values == null) ? List.of()
				: values.stream().filter(value -> value != null && !value.isBlank()).map(String::strip).distinct().toList();
	}

	public Long getId() {
		return this.id;
	}

	public String getName() {
		return this.name;
	}

	public List<String> getKeywords() {
		return this.keywords;
	}

	public List<String> getExcludedKeywords() {
		return this.excludedKeywords;
	}

	public List<String> getLocations() {
		return this.locations;
	}

	public boolean isRemoteOnly() {
		return this.remoteOnly;
	}

	public boolean isActive() {
		return this.active;
	}

	public Instant getCreatedAt() {
		return this.createdAt;
	}

}
