package dev.laranja.jobhunter.interviews;

import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import dev.laranja.jobhunter.interviews.InterviewDebriefView.RecurringQuestion;
import dev.laranja.jobhunter.shared.BusinessRuleException;
import dev.laranja.jobhunter.shared.NotFoundException;
import dev.laranja.jobhunter.shared.TextNormalizer;
import dev.laranja.jobhunter.shared.jdbc.JdbcRows;
import dev.laranja.jobhunter.tracking.ApplicationService;
import dev.laranja.jobhunter.tracking.ApplicationStatus;
import dev.laranja.jobhunter.tracking.NextActionKind;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.json.JsonMapper;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Registro pós-entrevista: o que perguntaram, o que foi bem, o que faltou, a nota e os próximos passos. Ao registrar,
 * agenda o follow-up na candidatura para 5 dias úteis depois da entrevista; as perguntas alimentam a próxima
 * preparação e o relatório semanal.
 */
@Service
@Transactional
public class InterviewDebriefs {

	/** Dias úteis entre a entrevista e o follow-up. */
	static final int FOLLOW_UP_BUSINESS_DAYS = 5;

	/** Hora do follow-up agendado, no horário de Brasília. */
	static final LocalTime FOLLOW_UP_TIME = LocalTime.of(9, 0);

	/** Perguntas recorrentes: as que aparecem em pelo menos este número de registros. */
	static final int RECURRING_MIN = 2;

	private static final ZoneId ZONE = ZoneId.of("America/Sao_Paulo");

	private final JdbcClient jdbc;

	private final JsonMapper json;

	private final ApplicationService applications;

	private final Clock clock;

	InterviewDebriefs(JdbcClient jdbc, JsonMapper json, ApplicationService applications, Clock clock) {
		this.jdbc = jdbc;
		this.json = json;
		this.applications = applications;
		this.clock = clock;
	}

	public InterviewDebriefView.Saved create(long applicationId, InterviewDebriefCommand command) {
		requireStage(command.stage());
		ApplicationStatus status = requireApplication(applicationId);
		Instant now = this.clock.instant();
		long id = this.jdbc.sql("""
				insert into interview_debrief (application_id, stage, interviewed_on, questions, went_well, to_improve,
				                               rating, next_steps, created_at, updated_at)
				values (:applicationId, :stage, :interviewedOn, cast(:questions as jsonb), :wentWell, :toImprove,
				        :rating, :nextSteps, :now, :now)
				returning id
				""")
			.param("applicationId", applicationId)
			.param("stage", command.stage().name())
			.param("interviewedOn", Date.valueOf(command.interviewedOn()))
			.param("questions", this.json.writeValueAsString(questions(command.questions())))
			.param("wentWell", blankToNull(command.wentWell()))
			.param("toImprove", blankToNull(command.toImprove()))
			.param("rating", command.rating())
			.param("nextSteps", blankToNull(command.nextSteps()))
			.param("now", Timestamp.from(now))
			.query(Long.class)
			.single();
		// Candidatura encerrada (recusa, desistência, proposta) não pede follow-up.
		Instant followUpAt = null;
		if (!status.isTerminal()) {
			followUpAt = followUpAt(command.interviewedOn());
			this.applications.scheduleNextAction(applicationId, followUpAt, NextActionKind.FOLLOW_UP);
		}
		return new InterviewDebriefView.Saved(get(id), followUpAt);
	}

	public InterviewDebriefView update(long id, InterviewDebriefCommand command) {
		requireStage(command.stage());
		int updated = this.jdbc.sql("""
				update interview_debrief set stage = :stage, interviewed_on = :interviewedOn,
				    questions = cast(:questions as jsonb), went_well = :wentWell, to_improve = :toImprove,
				    rating = :rating, next_steps = :nextSteps, updated_at = :now
				where id = :id
				""")
			.param("id", id)
			.param("stage", command.stage().name())
			.param("interviewedOn", Date.valueOf(command.interviewedOn()))
			.param("questions", this.json.writeValueAsString(questions(command.questions())))
			.param("wentWell", blankToNull(command.wentWell()))
			.param("toImprove", blankToNull(command.toImprove()))
			.param("rating", command.rating())
			.param("nextSteps", blankToNull(command.nextSteps()))
			.param("now", Timestamp.from(this.clock.instant()))
			.update();
		if (updated == 0) {
			throw NotFoundException.of("Registro de entrevista", id);
		}
		return get(id);
	}

	public void delete(long id) {
		if (this.jdbc.sql("delete from interview_debrief where id = :id").param("id", id).update() == 0) {
			throw NotFoundException.of("Registro de entrevista", id);
		}
	}

	@Transactional(readOnly = true)
	public InterviewDebriefView get(long id) {
		return this.jdbc.sql("select *, questions::text as questions_json from interview_debrief where id = :id")
			.param("id", id)
			.query((rs, row) -> toView(rs))
			.optional()
			.orElseThrow(() -> NotFoundException.of("Registro de entrevista", id));
	}

	@Transactional(readOnly = true)
	public List<InterviewDebriefView> forApplication(long applicationId) {
		requireApplication(applicationId);
		return this.jdbc.sql("""
				select *, questions::text as questions_json from interview_debrief
				where application_id = :applicationId order by interviewed_on desc, id desc
				""")
			.param("applicationId", applicationId)
			.query((rs, row) -> toView(rs))
			.list();
	}

	/**
	 * Perguntas que apareceram em {@value #RECURRING_MIN} ou mais registros, das mais frequentes para as menos. Duas
	 * perguntas são a mesma quando só mudam a caixa, os acentos e a pontuação.
	 */
	@Transactional(readOnly = true)
	public List<RecurringQuestion> recurringQuestions() {
		List<String> asked = this.jdbc.sql("""
				select q from interview_debrief d, jsonb_array_elements_text(d.questions) q
				order by d.interviewed_on desc, d.id desc
				""").query(String.class).list();
		return recurring(asked);
	}

	static List<RecurringQuestion> recurring(List<String> asked) {
		Map<String, String> label = new LinkedHashMap<>();
		Map<String, Integer> counts = new LinkedHashMap<>();
		for (String question : asked) {
			String key = TextNormalizer.normalize(question);
			if (key.isEmpty()) {
				continue;
			}
			label.putIfAbsent(key, question.strip());
			counts.merge(key, 1, Integer::sum);
		}
		List<RecurringQuestion> result = new ArrayList<>();
		counts.forEach((key, count) -> {
			if (count >= RECURRING_MIN) {
				result.add(new RecurringQuestion(label.get(key), count));
			}
		});
		result.sort((a, b) -> Integer.compare(b.count(), a.count()));
		return result;
	}

	/** Cinco dias úteis (segunda a sexta) depois da entrevista, às 9h. */
	static Instant followUpAt(LocalDate interviewedOn) {
		LocalDate day = interviewedOn;
		int added = 0;
		while (added < FOLLOW_UP_BUSINESS_DAYS) {
			day = day.plusDays(1);
			if (day.getDayOfWeek() != DayOfWeek.SATURDAY && day.getDayOfWeek() != DayOfWeek.SUNDAY) {
				added++;
			}
		}
		return day.atTime(FOLLOW_UP_TIME).atZone(ZONE).toInstant();
	}

	private InterviewDebriefView toView(ResultSet rs) throws SQLException {
		return new InterviewDebriefView(rs.getLong("id"), rs.getLong("application_id"),
				ApplicationStatus.valueOf(rs.getString("stage")), rs.getDate("interviewed_on").toLocalDate(),
				List.of(this.json.readValue(rs.getString("questions_json"), String[].class)), rs.getString("went_well"),
				rs.getString("to_improve"), (Integer) rs.getObject("rating", Integer.class), rs.getString("next_steps"),
				JdbcRows.instant(rs, "created_at"));
	}

	private ApplicationStatus requireApplication(long applicationId) {
		return this.jdbc.sql("select status from application where id = :id")
			.param("id", applicationId)
			.query((rs, row) -> ApplicationStatus.valueOf(rs.getString("status")))
			.optional()
			.orElseThrow(() -> NotFoundException.of("Candidatura", applicationId));
	}

	static void requireStage(ApplicationStatus stage) {
		if (stage != ApplicationStatus.ENTREVISTA && stage != ApplicationStatus.TESTE_TECNICO) {
			throw new BusinessRuleException("A etapa deve ser Entrevista ou Teste técnico.");
		}
	}

	private static List<String> questions(@Nullable List<String> questions) {
		return (questions == null) ? List.of()
				: questions.stream().filter(q -> q != null && !q.isBlank()).map(String::strip).toList();
	}

	private static @Nullable String blankToNull(@Nullable String value) {
		return (value == null || value.isBlank()) ? null : value.strip();
	}

}
