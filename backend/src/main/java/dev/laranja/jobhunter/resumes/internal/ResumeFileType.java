package dev.laranja.jobhunter.resumes.internal;

public enum ResumeFileType {

	PDF("application/pdf", "pdf"),

	DOCX("application/vnd.openxmlformats-officedocument.wordprocessingml.document", "docx");

	private final String contentType;

	private final String extension;

	ResumeFileType(String contentType, String extension) {
		this.contentType = contentType;
		this.extension = extension;
	}

	public String contentType() {
		return this.contentType;
	}

	public String extension() {
		return this.extension;
	}

}
