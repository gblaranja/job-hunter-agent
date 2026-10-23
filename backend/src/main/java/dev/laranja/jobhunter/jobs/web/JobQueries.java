package dev.laranja.jobhunter.jobs.web;

import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.Arrays;
import java.util.List;

import dev.laranja.jobhunter.jobs.FeedbackRating;
import dev.laranja.jobhunter.jobs.FeedbackReason;
import dev.laranja.jobhunter.jobs.JobOrigin;
import dev.laranja.jobhunter.jobs.JobView;
import dev.laranja.jobhunter.jobs.WorkModel;
import dev.laranja.jobhunter.shared.jdbc.JdbcRows;
import dev.laranja.jobhunter.shared.web.PageResponse;

import tools.jackson.databind.json.JsonMapper;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * Leituras da caixa de vagas e do detalhe. Juntam tabelas de outros módulos (candidatura, histórico,
 * currículo) direto no SQL, para que {@code jobs} não dependa deles em Java.
 */
@Component
class JobQueries {

	private static final String LIST_FROM = """
			from job j
			left join application a on a.job_id = j.id
			left join job_feedback f on f.job_id = j.id
			left join job_analysis an on an.job_id = j.id
			where (cast(:status as text) is null or coalesce(a.status, 'NOVA') = :status)
			  and (cast(:rating as text) is null or f.rating = :rating)
			  and (cast(:pattern as text) is null or j.title ilike :pattern or j.company ilike :pattern)
			  -- caixa de vagas: sem candidatura e sem "não gostei"
			  and (not :inbox or (a.id is null and coalesce(f.rating, '') <> 'NAO_GOSTEI'))
			""";

	private final JdbcClient jdbc;

	private final JsonMapper json;

	JobQueries(JdbcClient jdbc, JsonMapper json) {
		this.jdbc = jdbc;
		this.json = json;
	}

	PageResponse<JobListItem> list(JobListFilter filter, int page, int size) {
		String pattern = JdbcRows.containsPattern(filter.query());
		List<JobListItem> items = this.jdbc.sql("""
				select j.id, j.title, j.company, j.company_id, j.location, j.work_model, j.url, j.source, j.collected_at,
				       j.raw_description, a.id as application_id, coalesce(a.status, 'NOVA') as status,
				       f.rating, f.reasons, an.match_score, an.summary, an.matched_skills::text as matched,
				       an.gaps::text as gaps
				""" + LIST_FROM + """
				-- a caixa de vagas é ordenada por aderência; as demais listas, pela data da coleta
				order by case when :inbox then an.match_score end desc nulls last, j.collected_at desc, j.id desc
				limit :size offset :offset
				""")
			.param("status", filter.status(), Types.VARCHAR)
			.param("rating", (filter.rating() != null) ? filter.rating().name() : null, Types.VARCHAR)
			.param("pattern", pattern, Types.VARCHAR)
			.param("inbox", filter.inbox())
			.param("size", size)
			.param("offset", page * size)
			.query((rs, row) -> new JobListItem(rs.getLong("id"), rs.getString("title"), rs.getString("company"),
					JdbcRows.nullableLong(rs, "company_id"), rs.getString("location"),
					WorkModel.valueOf(rs.getString("work_model")), rs.getString("url"),
					JobOrigin.valueOf(rs.getString("source")), JdbcRows.instant(rs, "collected_at"),
					preview(rs.getString("raw_description")), JdbcRows.nullableLong(rs, "application_id"),
					rs.getString("status"), JdbcRows.nullableEnum(rs, "rating", FeedbackRating.class),
					reasons(rs), integer(rs, "match_score"), rs.getString("summary"), list(rs.getString("matched")),
					list(rs.getString("gaps"))))
			.list();
		long total = this.jdbc.sql("select count(*) " + LIST_FROM)
			.param("status", filter.status(), Types.VARCHAR)
			.param("rating", (filter.rating() != null) ? filter.rating().name() : null, Types.VARCHAR)
			.param("pattern", pattern, Types.VARCHAR)
			.param("inbox", filter.inbox())
			.query(Long.class)
			.single();
		return new PageResponse<>(items, page, size, total);
	}

	JobDetail detail(JobView job) {
		JobDetail.ApplicationSummary application = this.jdbc.sql("""
				select a.id, a.status, a.applied_at, a.channel, a.resume_version_id, r.label as resume_label,
				       a.notes, a.next_action_at
				from application a
				left join resume_version r on r.id = a.resume_version_id
				where a.job_id = :jobId
				""")
			.param("jobId", job.id())
			.query((rs, row) -> new JobDetail.ApplicationSummary(rs.getLong("id"), rs.getString("status"),
					JdbcRows.instant(rs, "applied_at"), rs.getString("channel"),
					JdbcRows.nullableLong(rs, "resume_version_id"), rs.getString("resume_label"),
					rs.getString("notes"), JdbcRows.instant(rs, "next_action_at")))
			.optional()
			.orElse(null);
		List<JobDetail.TimelineEvent> timeline = (application == null) ? List.of() : this.jdbc.sql("""
				select id, from_status, to_status, response_type, origin, note, created_at
				from application_event
				where application_id = :applicationId
				order by created_at, id
				""")
			.param("applicationId", application.id())
			.query((rs, row) -> new JobDetail.TimelineEvent(rs.getLong("id"), rs.getString("from_status"),
					rs.getString("to_status"), rs.getString("response_type"), rs.getString("origin"),
					rs.getString("note"), JdbcRows.instant(rs, "created_at")))
			.list();
		JobDetail.Feedback feedback = this.jdbc.sql("select rating, reasons, comment from job_feedback where job_id = :jobId")
			.param("jobId", job.id())
			.query((rs, row) -> new JobDetail.Feedback(FeedbackRating.valueOf(rs.getString("rating")), reasons(rs),
					rs.getString("comment")))
			.optional()
			.orElse(null);
		JobDetail.Analysis analysis = this.jdbc.sql("""
				select status, seniority, required_skills::text as required, nice_to_have::text as nice, summary,
				       employer_kind, contract_type, deterministic_score, llm_adjustment, match_score,
				       matched_skills::text as matched, gaps::text as gaps, justification, model_version, last_error,
				       analyzed_at
				from job_analysis where job_id = :jobId
				""")
			.param("jobId", job.id())
			.query((rs, row) -> new JobDetail.Analysis(rs.getString("status"), rs.getString("seniority"),
					list(rs.getString("required")), list(rs.getString("nice")), rs.getString("summary"),
					rs.getString("employer_kind"), rs.getString("contract_type"), integer(rs, "deterministic_score"),
					integer(rs, "llm_adjustment"), integer(rs, "match_score"), list(rs.getString("matched")),
					list(rs.getString("gaps")), rs.getString("justification"), rs.getString("model_version"),
					rs.getString("last_error"), JdbcRows.instant(rs, "analyzed_at")))
			.optional()
			.orElse(null);
		return new JobDetail(job, application, timeline, feedback, analysis);
	}

	@SuppressWarnings("unchecked")
	private List<String> list(String json) {
		return (json == null) ? List.of() : (List<String>) this.json.readValue(json, List.class);
	}

	private static Integer integer(ResultSet rs, String column) throws SQLException {
		int value = rs.getInt(column);
		return rs.wasNull() ? null : value;
	}

	private static List<FeedbackReason> reasons(ResultSet rs) throws SQLException {
		Array array = rs.getArray("reasons");
		if (array == null) {
			return List.of();
		}
		return Arrays.stream((String[]) array.getArray()).map(FeedbackReason::valueOf).toList();
	}

	// Começo da descrição em texto corrido, para o card da caixa de vagas (o resumo por IA chega na Fase 3).
	private static String preview(String description) {
		if (description == null) {
			return null;
		}
		String text = description.replaceAll("[#*_`>]+", "").replaceAll("\\s+", " ").strip();
		return (text.length() <= 280) ? text : text.substring(0, 280).strip() + "…";
	}

	record JobListFilter(String status, FeedbackRating rating, String query, boolean inbox) {
	}

}
