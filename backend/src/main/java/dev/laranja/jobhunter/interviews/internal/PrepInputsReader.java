package dev.laranja.jobhunter.interviews.internal;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;

import dev.laranja.jobhunter.interviews.InterviewDebriefs;
import dev.laranja.jobhunter.profile.CandidateProfiles;
import dev.laranja.jobhunter.shared.NotFoundException;
import dev.laranja.jobhunter.tracking.ApplicationStatus;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.json.JsonMapper;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/** Lê por SQL as entradas da preparação, como as telas de leitura que cruzam módulos. */
@Component
class PrepInputsReader {

	/** Histórias enviadas ao modelo; as mais recentes primeiro. */
	static final int MAX_STORIES = 30;

	private final JdbcClient jdbc;

	private final JsonMapper json;

	private final CandidateProfiles profiles;

	private final InterviewDebriefs debriefs;

	PrepInputsReader(JdbcClient jdbc, JsonMapper json, CandidateProfiles profiles, InterviewDebriefs debriefs) {
		this.jdbc = jdbc;
		this.json = json;
		this.profiles = profiles;
		this.debriefs = debriefs;
	}

	PrepInputs read(long applicationId, ApplicationStatus stage) {
		return this.jdbc.sql("""
				select a.id, a.job_id, coalesce(s.title, j.title) as title, coalesce(s.company, j.company) as company,
				       coalesce(s.description_md, j.raw_description) as description, j.location, j.work_model,
				       j.salary_range, an.seniority, an.required_skills::text as required_skills,
				       an.nice_to_have::text as nice_to_have, an.matched_skills::text as matched_skills,
				       an.gaps::text as gaps, an.summary, an.contract_type, an.employer_kind,
				       c.kind, c.sector, c.size_range, c.hq_location, c.website, c.watch_status,
				       (select g.level || coalesce(' ' || g.year, '') || coalesce(' · ' || g.ranking_name, '')
				        from company_gptw g where g.company_id = c.id limit 1) as gptw
				from application a
				join job j on j.id = a.job_id
				left join job_snapshot s on s.application_id = a.id
				left join job_analysis an on an.job_id = a.job_id
				left join company c on c.id = j.company_id
				where a.id = :id
				""")
			.param("id", applicationId)
			.query((rs, row) -> new PrepInputs(rs.getLong("id"), rs.getLong("job_id"), stage, rs.getString("title"),
					rs.getString("company"), rs.getString("description"), rs.getString("location"),
					rs.getString("work_model"), rs.getString("salary_range"), rs.getString("seniority"),
					strings(rs, "required_skills"), strings(rs, "nice_to_have"), strings(rs, "matched_skills"),
					strings(rs, "gaps"), rs.getString("summary"), rs.getString("contract_type"),
					rs.getString("employer_kind"), List.of(), List.of(),
					new PrepInputs.Company(rs.getString("kind"), rs.getString("sector"), rs.getString("size_range"),
							rs.getString("hq_location"), rs.getString("website"), rs.getString("watch_status"),
							rs.getString("gptw")),
					stories(), pastDebriefs(applicationId), this.debriefs.recurringQuestions(), this.profiles.current()))
			.optional()
			.orElseThrow(() -> NotFoundException.of("Candidatura", applicationId));
	}

	private List<PrepInputs.Story> stories() {
		return this.jdbc.sql("""
				select id, title, skills, situation, action, result from star_story
				order by updated_at desc, id desc limit :max
				""")
			.param("max", MAX_STORIES)
			.query((rs, row) -> new PrepInputs.Story(rs.getLong("id"), rs.getString("title"),
					Arrays.asList((String[]) rs.getArray("skills").getArray()), rs.getString("situation"),
					rs.getString("action"), rs.getString("result")))
			.list();
	}

	private List<PrepInputs.PastDebrief> pastDebriefs(long applicationId) {
		return this.jdbc.sql("""
				select stage, interviewed_on, questions::text as questions, to_improve, next_steps
				from interview_debrief where application_id = :id order by interviewed_on, id
				""")
			.param("id", applicationId)
			.query((rs, row) -> new PrepInputs.PastDebrief(ApplicationStatus.valueOf(rs.getString("stage")),
					rs.getDate("interviewed_on").toLocalDate().toString(), strings(rs, "questions"),
					rs.getString("to_improve"), rs.getString("next_steps")))
			.list();
	}

	private List<String> strings(ResultSet rs, String column) throws SQLException {
		@Nullable String value = rs.getString(column);
		return (value == null) ? List.of() : List.of(this.json.readValue(value, String[].class));
	}

}
