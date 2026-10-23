package dev.laranja.jobhunter.inbox;

import java.sql.Timestamp;
import java.sql.Types;
import java.time.Clock;
import java.time.Instant;
import java.util.List;

import dev.laranja.jobhunter.shared.NotFoundException;
import dev.laranja.jobhunter.shared.jdbc.JdbcRows;
import jakarta.validation.constraints.NotNull;
import org.jspecify.annotations.Nullable;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Revisão dos e-mails classificados: a palavra final é sua, e cada correção vira exemplo para o classificador. */
@Service
@Transactional
public class EmailReview {

	private final JdbcClient jdbc;

	private final ApplicationEventPublisher events;

	private final Clock clock;

	EmailReview(JdbcClient jdbc, ApplicationEventPublisher events, Clock clock) {
		this.jdbc = jdbc;
		this.events = events;
		this.clock = clock;
	}

	/**
	 * E-mails classificados, os que precisam de revisão primeiro e, dentro deles, os de menor confiança.
	 * @param pendingOnly só a fila de revisão
	 */
	@Transactional(readOnly = true)
	public List<EmailItem> list(boolean pendingOnly, @Nullable EmailClassification classification) {
		return this.jdbc.sql("""
				select e.id, e.sender, e.subject, e.snippet, e.received_at, e.ai_classification, e.ai_confidence,
				       e.final_classification, e.classified_by, e.company_guess, e.job_guess, e.ai_rationale,
				       e.needs_review, e.application_id, j.title as application_title, j.company as application_company
				from email_message e
				left join application a on a.id = e.application_id
				left join job j on j.id = a.job_id
				where e.classified_at is not null
				  and (not :pendingOnly or e.needs_review)
				  and (cast(:classification as text) is null
				       or coalesce(e.final_classification, e.ai_classification) = :classification)
				order by e.needs_review desc, case when e.needs_review then e.ai_confidence end asc nulls first,
				         e.received_at desc
				limit 200
				""")
			.param("pendingOnly", pendingOnly)
			.param("classification", (classification != null) ? classification.name() : null, Types.VARCHAR)
			.query((rs, row) -> new EmailItem(rs.getLong("id"), rs.getString("sender"), rs.getString("subject"),
					rs.getString("snippet"), JdbcRows.instant(rs, "received_at"),
					JdbcRows.nullableEnum(rs, "ai_classification", EmailClassification.class),
					(rs.getBigDecimal("ai_confidence") != null) ? rs.getBigDecimal("ai_confidence").doubleValue() : null,
					JdbcRows.nullableEnum(rs, "final_classification", EmailClassification.class),
					rs.getString("classified_by"), rs.getString("company_guess"), rs.getString("job_guess"),
					rs.getString("ai_rationale"), rs.getBoolean("needs_review"),
					JdbcRows.nullableLong(rs, "application_id"), rs.getString("application_title"),
					rs.getString("application_company")))
			.list();
	}

	/** Confirma ou corrige a classificação e a candidatura ligada; confirmar o que a IA disse conta como acerto dela. */
	public void review(long emailId, Review review) {
		EmailClassification ai = this.jdbc.sql("select ai_classification from email_message where id = :id")
			.param("id", emailId)
			.query((rs, row) -> JdbcRows.nullableEnum(rs, "ai_classification", EmailClassification.class))
			.optional()
			.orElseThrow(() -> NotFoundException.of("E-mail", emailId));
		String classifiedBy = (review.classification() == ai) ? "IA" : "USUARIO";
		this.jdbc.sql("""
				update email_message
				set final_classification = :classification, classified_by = :by, application_id = :application,
				    reviewed_at = :now, needs_review = false, processed = true
				where id = :id
				""")
			.param("classification", review.classification().name())
			.param("by", classifiedBy)
			.param("application", review.applicationId(), Types.BIGINT)
			.param("now", Timestamp.from(this.clock.instant()))
			.param("id", emailId)
			.update();
		this.events.publishEvent(new EmailClassificationCorrected(emailId, review.classification(), review.applicationId()));
	}

	/** Taxa de acerto da IA nos e-mails que você revisou. */
	@Transactional(readOnly = true)
	public Accuracy accuracy() {
		return this.jdbc.sql("""
				select count(*) as reviewed, count(*) filter (where classified_by = 'IA') as agreed,
				       count(*) filter (where needs_review) as pending
				from email_message where reviewed_at is not null or needs_review
				""")
			.query((rs, row) -> {
				long reviewed = rs.getLong("reviewed") - rs.getLong("pending");
				long agreed = rs.getLong("agreed");
				return new Accuracy(reviewed, agreed, rs.getLong("pending"), (reviewed > 0) ? (double) agreed / reviewed : null);
			})
			.single();
	}

	public record Review(@NotNull EmailClassification classification, @Nullable Long applicationId) {
	}

	public record EmailItem(long id, String sender, @Nullable String subject, @Nullable String snippet,
			Instant receivedAt, @Nullable EmailClassification aiClassification, @Nullable Double aiConfidence,
			@Nullable EmailClassification finalClassification, @Nullable String classifiedBy,
			@Nullable String companyGuess, @Nullable String jobGuess, @Nullable String rationale, boolean needsReview,
			@Nullable Long applicationId, @Nullable String applicationTitle, @Nullable String applicationCompany) {
	}

	/** @param rate acertos sobre revisados; nulo antes da primeira revisão */
	public record Accuracy(long reviewed, long agreed, long pending, @Nullable Double rate) {
	}

}
