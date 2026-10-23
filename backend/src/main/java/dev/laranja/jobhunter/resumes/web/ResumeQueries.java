package dev.laranja.jobhunter.resumes.web;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

import dev.laranja.jobhunter.shared.NotFoundException;
import dev.laranja.jobhunter.shared.jdbc.JdbcRows;

import tools.jackson.databind.json.JsonMapper;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/** Lista as versões com as métricas de uso, lidas das candidaturas direto no SQL. */
@Component
class ResumeQueries {

	private static final String SELECT = """
			select r.id, r.label, r.target_role, r.file_name, r.content_type, r.size_bytes, r.is_default, r.archived,
			       r.created_at, coalesce(r.skills, '[]'::jsonb)::text as skills, r.extracted_text is not null as has_text,
			       count(a.id) as applications,
			       count(a.id) filter (where exists (
			           select 1 from application_event e
			           where e.application_id = a.id and e.to_status in ('ENTREVISTA', 'TESTE_TECNICO', 'OFERTA')
			       )) as interviews
			from resume_version r
			left join application a on a.resume_version_id = r.id
			""";

	private final JdbcClient jdbc;

	private final JsonMapper json;

	ResumeQueries(JdbcClient jdbc, JsonMapper json) {
		this.jdbc = jdbc;
		this.json = json;
	}

	List<ResumeItem> list() {
		return this.jdbc.sql(SELECT + " group by r.id order by r.archived, r.is_default desc, r.created_at desc")
			.query((rs, row) -> toItem(rs))
			.list();
	}

	ResumeItem get(long id) {
		return this.jdbc.sql(SELECT + " where r.id = :id group by r.id")
			.param("id", id)
			.query((rs, row) -> toItem(rs))
			.optional()
			.orElseThrow(() -> NotFoundException.of("Currículo", id));
	}

	@SuppressWarnings("unchecked")
	private ResumeItem toItem(ResultSet rs) throws SQLException {
		long applications = rs.getLong("applications");
		long interviews = rs.getLong("interviews");
		Double interviewRate = (applications > 0) ? (double) interviews / applications : null;
		return new ResumeItem(rs.getLong("id"), rs.getString("label"), rs.getString("target_role"),
				rs.getString("file_name"), rs.getString("content_type"), rs.getLong("size_bytes"),
				rs.getBoolean("is_default"), rs.getBoolean("archived"), JdbcRows.instant(rs, "created_at"),
				applications, interviews, interviewRate, (List<String>) this.json.readValue(rs.getString("skills"), List.class),
				rs.getBoolean("has_text"));
	}

}
