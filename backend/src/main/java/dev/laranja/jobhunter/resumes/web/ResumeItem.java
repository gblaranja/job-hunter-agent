package dev.laranja.jobhunter.resumes.web;

import java.time.Instant;

/**
 * Versão do currículo na tela Currículos.
 *
 * @param interviewRate entrevistas sobre candidaturas com esta versão; {@code null} sem candidaturas
 */
record ResumeItem(long id, String label, String targetRole, String fileName, String contentType, long sizeBytes,
		boolean isDefault, boolean archived, Instant createdAt, long applications, long interviews,
		Double interviewRate) {
}
