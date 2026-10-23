package dev.laranja.jobhunter.jobs;

import java.sql.Timestamp;
import java.time.Clock;
import java.util.List;

import dev.laranja.jobhunter.shared.NotFoundException;
import jakarta.validation.constraints.NotNull;
import org.jspecify.annotations.Nullable;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Seu feedback sobre cada vaga (gostei, neutro, não gostei e por quê). */
@Service
@Transactional
public class JobFeedbackService {

	private final JdbcClient jdbc;

	private final Clock clock;

	JobFeedbackService(JdbcClient jdbc, Clock clock) {
		this.jdbc = jdbc;
		this.clock = clock;
	}

	/** Grava ou substitui o feedback da vaga. */
	public void save(long jobId, Feedback feedback) {
		boolean exists = this.jdbc.sql("select count(*) from job where id = :id")
			.param("id", jobId)
			.query(Long.class)
			.single() > 0;
		if (!exists) {
			throw NotFoundException.of("Vaga", jobId);
		}
		List<String> reasons = (feedback.reasons() == null) ? List.of()
				: feedback.reasons().stream().distinct().map(Enum::name).toList();
		Timestamp now = Timestamp.from(this.clock.instant());
		this.jdbc.sql("""
				insert into job_feedback (job_id, rating, reasons, comment, created_at, updated_at)
				-- os motivos vão como texto separado por vírgula e o Postgres monta o text[]
				values (:jobId, :rating, string_to_array(:reasons, ','), :comment, :now, :now)
				on conflict (job_id) do update
				set rating = excluded.rating, reasons = excluded.reasons, comment = excluded.comment,
				    updated_at = excluded.updated_at
				""")
			.param("jobId", jobId)
			.param("rating", feedback.rating().name())
			.param("reasons", String.join(",", reasons))
			.param("comment", (feedback.comment() == null || feedback.comment().isBlank()) ? null : feedback.comment().strip())
			.param("now", now)
			.update();
	}

	public record Feedback(@NotNull FeedbackRating rating, @Nullable List<FeedbackReason> reasons,
			@Nullable String comment) {
	}

}
