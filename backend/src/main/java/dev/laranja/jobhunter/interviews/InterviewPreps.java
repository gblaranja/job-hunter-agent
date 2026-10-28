package dev.laranja.jobhunter.interviews;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.Clock;
import java.util.List;

import dev.laranja.jobhunter.interviews.internal.InterviewPrepRequested;
import dev.laranja.jobhunter.shared.NotFoundException;
import dev.laranja.jobhunter.shared.jdbc.JdbcRows;
import dev.laranja.jobhunter.tracking.ApplicationStatus;
import tools.jackson.databind.json.JsonMapper;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Preparação para entrevista, uma por candidatura e etapa. O pedido grava GERANDO e a geração roda depois, fora da
 * requisição: com a Claude API pelo modelo das entrevistas, sem ela (ou se ela falhar) pelas regras.
 */
@Service
@Transactional
public class InterviewPreps {

	private static final String SELECT = """
			select p.*, p.content::text as content_json, a.job_id
			from interview_prep p join application a on a.id = p.application_id
			""";

	private final JdbcClient jdbc;

	private final JsonMapper json;

	private final ApplicationEventPublisher events;

	private final Clock clock;

	InterviewPreps(JdbcClient jdbc, JsonMapper json, ApplicationEventPublisher events, Clock clock) {
		this.jdbc = jdbc;
		this.json = json;
		this.events = events;
		this.clock = clock;
	}

	/**
	 * Pede (ou refaz) a preparação da etapa; sem etapa, usa a etapa atual da candidatura, ou Entrevista.
	 * @return a preparação em GERANDO
	 */
	public InterviewPrepView request(long applicationId, ApplicationStatus stage) {
		ApplicationStatus resolved = stage;
		if (resolved == null) {
			ApplicationStatus current = this.jdbc.sql("select status from application where id = :id")
				.param("id", applicationId)
				.query((rs, row) -> ApplicationStatus.valueOf(rs.getString("status")))
				.optional()
				.orElseThrow(() -> NotFoundException.of("Candidatura", applicationId));
			resolved = (current == ApplicationStatus.TESTE_TECNICO) ? current : ApplicationStatus.ENTREVISTA;
		}
		InterviewDebriefs.requireStage(resolved);
		requireApplication(applicationId);
		Instant now = this.clock.instant();
		long id = this.jdbc.sql("""
				insert into interview_prep (application_id, stage, status, created_at, updated_at)
				values (:applicationId, :stage, 'GERANDO', :now, :now)
				on conflict (application_id, stage) do update set status = 'GERANDO', last_error = null, updated_at = :now
				returning id
				""")
			.param("applicationId", applicationId)
			.param("stage", resolved.name())
			.param("now", Timestamp.from(now))
			.query(Long.class)
			.single();
		this.events.publishEvent(new InterviewPrepRequested(id));
		return get(id);
	}

	@Transactional(readOnly = true)
	public boolean exists(long applicationId, ApplicationStatus stage) {
		return this.jdbc.sql("select exists (select 1 from interview_prep where application_id = :id and stage = :stage)")
			.param("id", applicationId)
			.param("stage", stage.name())
			.query(Boolean.class)
			.single();
	}

	@Transactional(readOnly = true)
	public InterviewPrepView get(long id) {
		return this.jdbc.sql(SELECT + " where p.id = :id")
			.param("id", id)
			.query((rs, row) -> toView(rs))
			.optional()
			.orElseThrow(() -> NotFoundException.of("Preparação", id));
	}

	/** As preparações da candidatura, a mais recente primeiro. */
	@Transactional(readOnly = true)
	public List<InterviewPrepView> forApplication(long applicationId) {
		requireApplication(applicationId);
		return this.jdbc.sql(SELECT + " where p.application_id = :id order by p.updated_at desc, p.id desc")
			.param("id", applicationId)
			.query((rs, row) -> toView(rs))
			.list();
	}

	private InterviewPrepView toView(ResultSet rs) throws SQLException {
		String content = rs.getString("content_json");
		return new InterviewPrepView(rs.getLong("id"), rs.getLong("application_id"), rs.getLong("job_id"),
				ApplicationStatus.valueOf(rs.getString("stage")), rs.getString("status"),
				(content != null) ? this.json.readValue(content, InterviewPrepContent.class) : null,
				rs.getString("source"), rs.getString("model_version"), rs.getString("last_error"),
				JdbcRows.instant(rs, "created_at"), JdbcRows.instant(rs, "updated_at"));
	}

	private void requireApplication(long applicationId) {
		if (!this.jdbc.sql("select exists (select 1 from application where id = :id)")
			.param("id", applicationId)
			.query(Boolean.class)
			.single()) {
			throw NotFoundException.of("Candidatura", applicationId);
		}
	}


}
