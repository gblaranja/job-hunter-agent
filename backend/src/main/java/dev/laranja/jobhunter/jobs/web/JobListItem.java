package dev.laranja.jobhunter.jobs.web;

import java.time.Instant;

import dev.laranja.jobhunter.jobs.JobOrigin;
import dev.laranja.jobhunter.jobs.WorkModel;

/**
 * Linha da caixa de vagas.
 *
 * @param status status da candidatura, ou {@code NOVA} quando a vaga ainda não tem candidatura
 */
record JobListItem(long id, String title, String company, String location, WorkModel workModel, String url,
		JobOrigin origin, Instant collectedAt, Long applicationId, String status) {
}
