package dev.laranja.jobhunter.jobs.web;

import java.time.Instant;

import dev.laranja.jobhunter.jobs.JobOrigin;
import dev.laranja.jobhunter.jobs.WorkModel;
import org.jspecify.annotations.Nullable;

/**
 * Linha da caixa de vagas.
 *
 * @param status status da candidatura, ou {@code NOVA} quando a vaga ainda não tem candidatura
 */
record JobListItem(long id, String title, String company, @Nullable String location, WorkModel workModel,
		@Nullable String url, JobOrigin origin, Instant collectedAt, @Nullable Long applicationId, String status) {
}
