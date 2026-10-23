package dev.laranja.jobhunter.tracking.web;

import java.sql.Types;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import dev.laranja.jobhunter.shared.NotFoundException;
import dev.laranja.jobhunter.shared.jdbc.JdbcRows;
import dev.laranja.jobhunter.tracking.ApplicationChannel;
import dev.laranja.jobhunter.tracking.ApplicationStatus;
import dev.laranja.jobhunter.tracking.CaptureSource;
import dev.laranja.jobhunter.tracking.LinkStatus;
import dev.laranja.jobhunter.tracking.web.ApplicationViews.AppliedJob;
import dev.laranja.jobhunter.tracking.web.ApplicationViews.AppliedListItem;
import dev.laranja.jobhunter.tracking.web.ApplicationViews.Board;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/** Leituras do kanban, da lista de candidaturas e do AppliedJobCard. */
@Component
class ApplicationQueries {

	private static final int EXCERPT_RADIUS = 90;

	private final JdbcClient jdbc;

	private final Clock clock;

	ApplicationQueries(JdbcClient jdbc, Clock clock) {
		this.jdbc = jdbc;
		this.clock = clock;
	}

	Board board() {
		Instant now = this.clock.instant();
		List<Board.Card> cards = this.jdbc.sql("""
				select a.id, a.job_id, coalesce(s.title, j.title) as title, coalesce(s.company, j.company) as company,
				       a.status, a.resume_version_id, r.label as resume_label, a.next_action_at, s.link_status,
				       coalesce((select max(e.created_at) from application_event e
				                 where e.application_id = a.id and e.to_status = a.status), a.created_at) as status_since
				from application a
				join job j on j.id = a.job_id
				left join job_snapshot s on s.application_id = a.id
				left join resume_version r on r.id = a.resume_version_id
				order by status_since desc, a.id desc
				""")
			.query((rs, row) -> new Board.Card(rs.getLong("id"), rs.getLong("job_id"), rs.getString("title"),
					rs.getString("company"), ApplicationStatus.valueOf(rs.getString("status")),
					Duration.between(JdbcRows.instant(rs, "status_since"), now).toDays(),
					JdbcRows.nullableLong(rs, "resume_version_id"), rs.getString("resume_label"),
					JdbcRows.instant(rs, "next_action_at"), JdbcRows.nullableEnum(rs, "link_status", LinkStatus.class)))
			.list();

		Map<ApplicationStatus, List<Board.Card>> byStatus = new EnumMap<>(ApplicationStatus.class);
		cards.forEach(card -> byStatus.computeIfAbsent(card.status(), status -> new ArrayList<>()).add(card));
		List<Board.Column> columns = Arrays.stream(ApplicationStatus.values())
			.filter(status -> status != ApplicationStatus.NOVA)
			.map(status -> new Board.Column(status, byStatus.getOrDefault(status, List.of())))
			.toList();
		return new Board(columns);
	}

	List<AppliedListItem> list(ApplicationStatus status, String query) {
		String pattern = JdbcRows.containsPattern(query);
		return this.jdbc.sql("""
				select a.id, a.job_id, s.title, s.company, a.status, a.applied_at, a.channel, s.job_url, s.link_status,
				       a.resume_version_id, r.label as resume_label, s.description_md
				from application a
				join job_snapshot s on s.application_id = a.id
				left join resume_version r on r.id = a.resume_version_id
				where (cast(:status as text) is null or a.status = :status)
				  and (cast(:pattern as text) is null
				       or s.title ilike :pattern or s.company ilike :pattern or s.description_md ilike :pattern)
				order by a.applied_at desc nulls last, a.id desc
				""")
			.param("status", (status != null) ? status.name() : null, Types.VARCHAR)
			.param("pattern", pattern, Types.VARCHAR)
			.query((rs, row) -> new AppliedListItem(rs.getLong("id"), rs.getLong("job_id"), rs.getString("title"),
					rs.getString("company"), ApplicationStatus.valueOf(rs.getString("status")),
					JdbcRows.instant(rs, "applied_at"),
					JdbcRows.nullableEnum(rs, "channel", ApplicationChannel.class), rs.getString("job_url"),
					LinkStatus.valueOf(rs.getString("link_status")), JdbcRows.nullableLong(rs, "resume_version_id"),
					rs.getString("resume_label"), excerpt(rs.getString("description_md"), query)))
			.list();
	}

	AppliedJob appliedJob(long applicationId) {
		return this.jdbc.sql("""
				select a.id, a.job_id, a.status, a.applied_at, a.channel,
				       r.id as resume_id, r.label as resume_label, r.file_name as resume_file_name,
				       s.job_url, s.link_status, s.link_checked_at, s.title, s.company, s.description_md,
				       s.capture_source, s.page_pdf_key, s.captured_at
				from application a
				join job_snapshot s on s.application_id = a.id
				left join resume_version r on r.id = a.resume_version_id
				where a.id = :id
				""")
			.param("id", applicationId)
			.query((rs, row) -> {
				Long resumeId = JdbcRows.nullableLong(rs, "resume_id");
				AppliedJob.Resume resume = (resumeId != null)
						? new AppliedJob.Resume(resumeId, rs.getString("resume_label"), rs.getString("resume_file_name"))
						: null;
				AppliedJob.Snapshot snapshot = new AppliedJob.Snapshot(rs.getString("job_url"),
						LinkStatus.valueOf(rs.getString("link_status")), JdbcRows.instant(rs, "link_checked_at"),
						rs.getString("title"), rs.getString("company"), rs.getString("description_md"),
						CaptureSource.valueOf(rs.getString("capture_source")), rs.getString("page_pdf_key") != null,
						JdbcRows.instant(rs, "captured_at"));
				return new AppliedJob(rs.getLong("id"), rs.getLong("job_id"),
						ApplicationStatus.valueOf(rs.getString("status")), JdbcRows.instant(rs, "applied_at"),
						JdbcRows.nullableEnum(rs, "channel", ApplicationChannel.class), resume, snapshot);
			})
			.optional()
			.orElseThrow(() -> new NotFoundException("A candidatura " + applicationId + " ainda não tem a vaga salva."));
	}

	// Trecho da descrição em volta da primeira ocorrência do termo, para a lista mostrar onde ele aparece.
	static String excerpt(String description, String query) {
		if (description == null || query == null || query.isBlank()) {
			return null;
		}
		String term = query.strip().toLowerCase(Locale.ROOT);
		int index = description.toLowerCase(Locale.ROOT).indexOf(term);
		if (index < 0) {
			return null;
		}
		int start = Math.max(0, index - EXCERPT_RADIUS);
		int end = Math.min(description.length(), index + term.length() + EXCERPT_RADIUS);
		// Sem a marcação do Markdown: o trecho aparece como texto corrido na tabela.
		String excerpt = description.substring(start, end).replaceAll("[#*_`>]+", "").replaceAll("\\s+", " ").strip();
		return ((start > 0) ? "…" : "") + excerpt + ((end < description.length()) ? "…" : "");
	}

}
