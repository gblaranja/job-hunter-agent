package dev.laranja.jobhunter.jobs;

import java.sql.Timestamp;
import java.time.Clock;
import java.util.Collection;

import dev.laranja.jobhunter.shared.NotFoundException;
import jakarta.validation.constraints.NotNull;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Descarte de vagas da caixa ("não tenho interesse", "não tenho o perfil técnico") e o caminho de volta. O descarte
 * automático por família de cargo usa o motivo "sem perfil técnico", com o detalhe da família.
 */
@Service
@Transactional
public class JobDismissalService {

	private final JdbcClient jdbc;

	private final Clock clock;

	JobDismissalService(JdbcClient jdbc, Clock clock) {
		this.jdbc = jdbc;
		this.clock = clock;
	}

	/** Tira a vaga da caixa; descartar de novo só troca o motivo (e apaga o detalhe de um descarte automático). */
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
				on conflict (job_id) do update set reason = excluded.reason, detail = null, created_at = excluded.created_at
				""")
			.param("jobId", jobId)
			.param("reason", dismissal.reason().name())
			.param("now", Timestamp.from(this.clock.instant()))
			.update();
	}

	/**
	 * Tira da caixa uma vaga recém-coletada cuja família de cargo não está entre as escolhidas nos perfis de busca.
	 * Não mexe em vaga já descartada.
	 */
	public void dismissOutsideFamilies(long jobId, JobFamily family) {
		this.jdbc.sql("""
				insert into job_dismissal (job_id, reason, detail, created_at)
				values (:jobId, 'SEM_PERFIL_TECNICO', :detail, :now)
				on conflict (job_id) do nothing
				""")
			.param("jobId", jobId)
			.param("detail", detail(family))
			.param("now", Timestamp.from(this.clock.instant()))
			.update();
	}

	/**
	 * As famílias escolhidas nos perfis mudaram: tira da caixa as vagas abertas de outra família. Vaga sem família
	 * reconhecida fica; sem nenhuma família escolhida, nada sai.
	 * @return quantas vagas saíram da caixa
	 */
	public int dismissOutsideFamilies(Collection<JobFamily> accepted) {
		if (accepted.isEmpty()) {
			return 0;
		}
		int dismissed = 0;
		for (JobFamily family : JobFamily.values()) {
			if (accepted.contains(family)) {
				continue;
			}
			dismissed += this.jdbc.sql("""
					insert into job_dismissal (job_id, reason, detail, created_at)
					select j.id, 'SEM_PERFIL_TECNICO', :detail, :now
					from job j
					left join application a on a.job_id = j.id
					left join job_feedback f on f.job_id = j.id
					where j.job_family = :family and j.closed_at is null and a.id is null
					  and coalesce(f.rating, '') <> 'NAO_GOSTEI'
					on conflict (job_id) do nothing
					""")
				.param("detail", detail(family))
				.param("family", family.name())
				.param("now", Timestamp.from(this.clock.instant()))
				.update();
		}
		return dismissed;
	}

	private static String detail(JobFamily family) {
		return "Família de cargo fora dos perfis de busca: " + family.label();
	}

	/** Devolve a vaga à caixa. */
	public void restore(long jobId) {
		this.jdbc.sql("delete from job_dismissal where job_id = :jobId").param("jobId", jobId).update();
	}

	public record Dismissal(@NotNull DismissReason reason) {
	}

}
