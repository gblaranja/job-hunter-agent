package dev.laranja.jobhunter.jobs;

import java.time.Instant;

import org.jspecify.annotations.Nullable;

/** A vaga como os outros módulos a enxergam. */
public record JobView(long id, JobOrigin origin, String title, String company, @Nullable String location,
		WorkModel workModel, JobScope scope, @Nullable String salaryRange, @Nullable String url,
		@Nullable String description, Instant collectedAt) {
}
