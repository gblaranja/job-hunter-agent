package dev.laranja.jobhunter.resumes.internal;

import java.time.Instant;
import java.util.List;

import dev.laranja.jobhunter.shared.BusinessRuleException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "resume_version")
public class ResumeVersion {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	private String label;

	private String targetRole;

	private String fileKey;

	private String fileName;

	private String contentType;

	private long sizeBytes;

	private String sha256;

	private String extractedText;

	@JdbcTypeCode(SqlTypes.JSON)
	private List<String> skills;

	@Column(name = "is_default")
	private boolean defaultVersion;

	private boolean archived;

	private Instant createdAt;

	protected ResumeVersion() {
	}

	public static ResumeVersion create(String label, String targetRole, String fileKey, String fileName,
			ResumeFileType type, long sizeBytes, String sha256, boolean defaultVersion, Instant now) {
		ResumeVersion resume = new ResumeVersion();
		resume.label = label;
		resume.targetRole = targetRole;
		resume.fileKey = fileKey;
		resume.fileName = fileName;
		resume.contentType = type.contentType();
		resume.sizeBytes = sizeBytes;
		resume.sha256 = sha256;
		resume.defaultVersion = defaultVersion;
		resume.createdAt = now;
		return resume;
	}

	public void rename(String label) {
		if (label.isBlank()) {
			throw new BusinessRuleException("O rótulo do currículo não pode ficar vazio.");
		}
		this.label = label.strip();
	}

	public void changeTargetRole(String targetRole) {
		this.targetRole = (targetRole == null || targetRole.isBlank()) ? null : targetRole.strip();
	}

	public void useExtractedText(String text) {
		this.extractedText = text;
	}

	public void useSkills(List<String> skills) {
		this.skills = List.copyOf(skills);
	}

	public void markAsDefault() {
		if (this.archived) {
			throw new BusinessRuleException("Desarquive o currículo antes de defini-lo como padrão.");
		}
		this.defaultVersion = true;
	}

	public void archive() {
		if (this.defaultVersion) {
			throw new BusinessRuleException("O currículo padrão não pode ser arquivado; escolha outro como padrão antes.");
		}
		this.archived = true;
	}

	public void unarchive() {
		this.archived = false;
	}

	public Long getId() {
		return this.id;
	}

	public String getLabel() {
		return this.label;
	}

	public String getFileKey() {
		return this.fileKey;
	}

	public String getFileName() {
		return this.fileName;
	}

	public boolean isDefaultVersion() {
		return this.defaultVersion;
	}

	public boolean isArchived() {
		return this.archived;
	}

	public String getExtractedText() {
		return this.extractedText;
	}

	public List<String> getSkills() {
		return (this.skills != null) ? this.skills : List.of();
	}

	public String getTargetRole() {
		return this.targetRole;
	}

}
