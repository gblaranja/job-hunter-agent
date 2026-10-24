package dev.laranja.jobhunter.jobs;

import java.sql.Timestamp;
import java.time.Clock;

import dev.laranja.jobhunter.shared.NotFoundException;
import jakarta.validation.constraints.NotNull;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Descarte de vagas da caixa ("não tenho interesse", "não tenho o perfil técnico") e o caminho de volta. */
@Service
@Transactional
public class JobDismissalService {

	private final JdbcClient jdbc;

	private final Clock clock;

	JobDismissalService(JdbcClient jdbc, Clock clock) {
		this.jdbc = jdbc;
		this.clock = clock;
	}

	/** Tira a vaga da caixa; descartar de novo só troca o motivo. */
	public void dismiss(long jobId, Dismissal dismissal) {
		boolean exists = this.jdbc.sql("select count(*) from job where id = :id")
			.param("id", jobId)
			.query(Long.class)
			.single() > 0;
		if (!exists) {
			throw NotFoundException.of("Vaga", jobId);
		}
		this.jdbc.sql("""
				insert into job_dismissal (job_id, reason, created_at)
				values (:jobId, :reason, :now)
				on conflict (job_id) do update set reason = excluded.reason, created_at = excluded.created_at
				""")
			.param("jobId", jobId)
			.param("reason", dismissal.reason().name())
			.param("now", Timestamp.from(this.clock.instant()))
			.update();
	}

	/** Devolve a vaga à caixa. */
	public void restore(long jobId) {
		this.jdbc.sql("delete from job_dismissal where job_id = :jobId").param("jobId", jobId).update();
	}

	public record Dismissal(@NotNull DismissReason reason) {
	}

}
