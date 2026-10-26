package dev.laranja.jobhunter.agenda;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.List;

import dev.laranja.jobhunter.agenda.TodayAgenda.TodayEmail;
import dev.laranja.jobhunter.agenda.TodayAgenda.TodayFollowUp;
import dev.laranja.jobhunter.agenda.TodayAgenda.TodayInterview;
import dev.laranja.jobhunter.agenda.TodayAgenda.TodayJob;
import dev.laranja.jobhunter.agenda.TodayAgenda.TodayTailoredResume;
import dev.laranja.jobhunter.agenda.TodayAgenda.TodayWeeklyGoal;
import dev.laranja.jobhunter.shared.jdbc.JdbcRows;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Monta a fila do dia direto no SQL, lendo as tabelas de vagas, candidaturas, e-mails, currículos e perfil. */
@Service
@Transactional(readOnly = true)
public class TodayQueries {

	/** Score mínimo das vagas para aplicar hoje e das candidaturas que contam como aderentes na meta. */
	public static final int APPLY_SCORE = 70;

	static final ZoneId ZONE = ZoneId.of("America/Sao_Paulo");

	private static final int MAX_JOBS = 10;

	private static final String FAILED_KNOCKOUTS = """
			(select count(*) from jsonb_array_elements(coalesce(an.knockouts, '[]'::jsonb)) k
			 where k->>'status' = 'NAO_ATENDE')""";

	private final JdbcClient jdbc;

	private final Clock clock;

	TodayQueries(JdbcClient jdbc, Clock clock) {
		this.jdbc = jdbc;
		this.clock = clock;
	}

	public TodayAgenda today() {
		Instant now = this.clock.instant();
		LocalDate day = LocalDate.ofInstant(now, ZONE);
		LocalDate weekStart = day.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
		return new TodayAgenda(day, jobs(now), followUps(now), emails(), interviews(now), tailoredResumes(),
				weeklyGoal(weekStart));
	}

	private List<TodayJob> jobs(Instant now) {
		return this.jdbc.sql("""
				select j.id, j.title, j.company, an.match_score, j.first_seen_at, j.legitimacy, j.url
				from job j
				join job_analysis an on an.job_id = j.id
				left join application a on a.job_id = j.id
				left join job_feedback f on f.job_id = j.id
				left join job_dismissal d on d.job_id = j.id
				where j.closed_at is null and j.duplicate_of_job_id is null and a.id is null and d.job_id is null
				  and coalesce(f.rating, '') <> 'NAO_GOSTEI'
				  and an.match_score >= :score
				  and coalesce(j.legitimacy, 'ALTA') in ('ALTA', 'MEDIA')
				  and j.first_seen_at >= :since
				  and """ + FAILED_KNOCKOUTS + """
				 = 0
				order by an.match_score desc, j.first_seen_at desc, j.id
				limit :limit
				""")
			.param("score", APPLY_SCORE)
			.param("since", Timestamp.from(now.minusSeconds(72 * 3600)))
			.param("limit", MAX_JOBS)
			.query((rs, row) -> new TodayJob(rs.getLong("id"), rs.getString("title"), rs.getString("company"),
					rs.getInt("match_score"), JdbcRows.instant(rs, "first_seen_at"), rs.getString("legitimacy"),
					rs.getString("url")))
			.list();
	}

	private List<TodayFollowUp> followUps(Instant now) {
		return this.jdbc.sql("""
				select a.id, a.job_id, j.title, j.company, a.status, a.next_action_at, a.next_action_kind,
				       a.follow_ups_sent
				from application a join job j on j.id = a.job_id
				where a.next_action_at <= :now and a.status not in ('RECUSADA', 'DESISTI', 'ENCERRADA')
				order by a.next_action_at, a.id
				""")
			.param("now", Timestamp.from(now))
			.query((rs, row) -> new TodayFollowUp(rs.getLong("id"), rs.getLong("job_id"), rs.getString("title"),
					rs.getString("company"), rs.getString("status"), JdbcRows.instant(rs, "next_action_at"),
					rs.getString("next_action_kind"), rs.getInt("follow_ups_sent")))
			.list();
	}

	private List<TodayEmail> emails() {
		return this.jdbc.sql("""
				select id, sender, subject, received_at, coalesce(final_classification, ai_classification) as classification
				from email_message where needs_review
				order by received_at desc limit 20
				""")
			.query((rs, row) -> new TodayEmail(rs.getLong("id"), rs.getString("sender"), rs.getString("subject"),
					JdbcRows.instant(rs, "received_at"), rs.getString("classification")))
			.list();
	}

	private List<TodayInterview> interviews(Instant now) {
		return this.jdbc.sql("""
				select a.id, a.job_id, j.title, j.company, a.status, a.next_action_at
				from application a join job j on j.id = a.job_id
				where a.status in ('ENTREVISTA', 'TESTE_TECNICO')
				   or (a.next_action_kind = 'ENTREVISTA' and a.next_action_at between :now and :weekEnd
				       and a.status not in ('RECUSADA', 'DESISTI', 'ENCERRADA'))
				order by (a.next_action_at between :now and :weekEnd) desc, a.next_action_at nulls last, a.id
				""")
			.param("now", Timestamp.from(now))
			.param("weekEnd", Timestamp.from(now.plusSeconds(7 * 86400)))
			.query((rs, row) -> new TodayInterview(rs.getLong("id"), rs.getLong("job_id"), rs.getString("title"),
					rs.getString("company"), rs.getString("status"), JdbcRows.instant(rs, "next_action_at")))
			.list();
	}

	private List<TodayTailoredResume> tailoredResumes() {
		return this.jdbc.sql("""
				select distinct on (t.job_id) t.id, t.job_id, j.title, j.company, t.updated_at
				from tailored_resume t join job j on j.id = t.job_id
				left join application a on a.job_id = j.id
				where t.status = 'PRONTO' and j.closed_at is null and (a.id is null or a.status = 'INTERESSE')
				order by t.job_id, t.updated_at desc
				""")
			.query((rs, row) -> new TodayTailoredResume(rs.getLong("id"), rs.getLong("job_id"), rs.getString("title"),
					rs.getString("company"), JdbcRows.instant(rs, "updated_at")))
			.list();
	}

	private TodayWeeklyGoal weeklyGoal(LocalDate weekStart) {
		record Counts(int applied, int aligned, int goal) {
		}
		Counts counts = this.jdbc.sql("""
				select count(a.id) as applied,
				       count(a.id) filter (where an.match_score >= :score and """ + FAILED_KNOCKOUTS + """
				 = 0) as aligned,
				       coalesce((select weekly_goal from candidate_profile where id = 1), 12) as goal
				from application a
				left join job_analysis an on an.job_id = a.job_id
				where a.applied_at >= cast(:weekStart as date) at time zone 'America/Sao_Paulo'
				""")
			.param("score", APPLY_SCORE)
			.param("weekStart", Date.valueOf(weekStart))
			.query(Counts.class)
			.single();
		return new TodayWeeklyGoal(weekStart, counts.applied(), counts.aligned(), counts.goal());
	}

}
