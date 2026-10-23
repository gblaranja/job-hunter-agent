package dev.laranja.jobhunter.jobs;

import java.time.Instant;

/** A vaga como os outros módulos a enxergam. */
public record JobView(long id, JobOrigin origin, String title, String company, String location, WorkModel workModel,
		String salaryRange, String url, String description, Instant collectedAt) {
}
