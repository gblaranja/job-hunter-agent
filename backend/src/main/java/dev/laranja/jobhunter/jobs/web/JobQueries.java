package dev.laranja.jobhunter.jobs.web;

import java.sql.Types;
import java.util.List;

import dev.laranja.jobhunter.jobs.JobOrigin;
import dev.laranja.jobhunter.jobs.JobView;
import dev.laranja.jobhunter.jobs.WorkModel;
import dev.laranja.jobhunter.shared.jdbc.JdbcRows;
import dev.laranja.jobhunter.shared.web.PageResponse;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * Leituras da caixa de vagas e do detalhe. Juntam tabelas de outros módulos (candidatura, histórico,
 * currículo) direto no SQL, para que {@code jobs} não dependa deles em Java.
 */
@Component
class JobQueries {

	private static final String LIST_FILTER = """
			where (cast(:status as text) is null or coalesce(a.status, 'NOVA') = :status)
			  and (cast(:pattern as text) is null or j.title ilike :pattern or j.company ilike :pattern)
			""";

	private final JdbcClient jdbc;

	JobQueries(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	PageResponse<JobListItem> list(String status, String query, int page, int size) {
		String pattern = JdbcRows.containsPattern(query);
		List<JobListItem> items = this.jdbc.sql("""
				select j.id, j.title, j.company, j.location, j.work_model, j.url, j.source, j.collected_at,
				       a.id as application_id, coalesce(a.status, 'NOVA') as status
				from job j
				left join application a on a.job_id = j.id
				""" + LIST_FILTER + """
				order by j.collected_at desc, j.id desc
				limit :size offset :offset
				""")
			.param("status", status, Types.VARCHAR)
			.param("pattern", pattern, Types.VARCHAR)
			.param("size", size)
			.param("offset", page * size)
			.query((rs, row) -> new JobListItem(rs.getLong("id"), rs.getString("title"), rs.getString("company"),
					rs.getString("location"), WorkModel.valueOf(rs.getString("work_model")), rs.getString("url"),
					JobOrigin.valueOf(rs.getString("source")), JdbcRows.instant(rs, "collected_at"),
					JdbcRows.nullableLong(rs, "application_id"), rs.getString("status")))
			.list();
		long total = this.jdbc.sql("select count(*) from job j left join application a on a.job_id = j.id " + LIST_FILTER)
			.param("status", status, Types.VARCHAR)
			.param("pattern", pattern, Types.VARCHAR)
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
		return new JobDetail(job, application, timeline);
	}

}
