package dev.laranja.jobhunter.resumes.web;

import java.time.Instant;

import org.jspecify.annotations.Nullable;

/**
 * Versão do currículo na tela Currículos.
 *
 * @param interviewRate entrevistas sobre candidaturas com esta versão; {@code null} sem candidaturas
 */
record ResumeItem(long id, String label, @Nullable String targetRole, String fileName, String contentType,
		long sizeBytes, boolean isDefault, boolean archived, Instant createdAt, long applications, long interviews,
		@Nullable Double interviewRate) {
}
