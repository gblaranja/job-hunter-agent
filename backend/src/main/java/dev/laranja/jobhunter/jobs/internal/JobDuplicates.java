package dev.laranja.jobhunter.jobs.internal;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.Optional;
import java.util.stream.Collectors;

import dev.laranja.jobhunter.jobs.JobOrigin;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * A mesma vaga publicada em fontes diferentes (o alerta do LinkedIn e o ATS da empresa, por exemplo). A vaga nova é
 * comparada com as abertas da mesma empresa nos últimos 60 dias: título parecido (similaridade trigram a partir de
 * 0,6) e local compatível. Uma das duas fica como canônica e a outra aponta para ela em {@code duplicate_of_job_id}.
 */
@Component
public class JobDuplicates {

	static final double TITLE_SIMILARITY = 0.6;

	private static final Duration WINDOW = Duration.ofDays(60);

	private final JdbcClient jdbc;

	JobDuplicates(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	/** A vaga já existente, de outra fonte, que é a mesma que a recém-criada. */
	public Optional<Match> find(long newJobId, Instant now) {
		return this.jdbc.sql("""
				select o.id as existing_id, o.source as existing_source,
				       length(coalesce(o.raw_description, '')) as existing_length, n.source as new_source,
				       length(coalesce(n.raw_description, '')) as new_length,
				       (exists (select 1 from application a where a.job_id = o.id)
				        or exists (select 1 from job_feedback f where f.job_id = o.id)) as existing_engaged
				from job n
				join job o on o.company_id = n.company_id and o.id <> n.id
				where n.id = :jobId and o.source <> n.source and o.closed_at is null and o.duplicate_of_job_id is null
				  and o.first_seen_at >= :since
				  and similarity(lower(o.title), lower(n.title)) >= :similarity
				  -- local compatível: um deles sem local, locais parecidos ou os dois remotos
				  and (o.location is null or n.location is null
				       or similarity(lower(o.location), lower(n.location)) >= 0.5
				       or (o.work_model = 'REMOTO' and n.work_model = 'REMOTO'))
				order by similarity(lower(o.title), lower(n.title)) desc, o.id
				limit 1
				""")
			.param("jobId", newJobId)
			.param("since", Timestamp.from(now.minus(WINDOW)))
			.param("similarity", TITLE_SIMILARITY)
			.query((rs, row) -> new Match(newJobId, rs.getLong("existing_id"),
					JobOrigin.valueOf(rs.getString("new_source")), JobOrigin.valueOf(rs.getString("existing_source")),
					rs.getInt("new_length"), rs.getInt("existing_length"), rs.getBoolean("existing_engaged")))
			.optional();
	}

	/**
	 * Liga a cópia à canônica. As cópias que apontavam para a vaga que deixou de ser canônica passam a apontar para a
	 * nova, e a marcação de republicação entre as duas é desfeita (são a mesma vaga, não uma reabertura).
	 */
	public void link(long duplicateId, long canonicalId) {
		this.jdbc.sql("update job set duplicate_of_job_id = :canonical where duplicate_of_job_id = :duplicate")
			.param("canonical", canonicalId)
			.param("duplicate", duplicateId)
			.update();
		this.jdbc.sql("""
				update job set duplicate_of_job_id = case when id = :duplicate then :canonical else duplicate_of_job_id end,
				    repost_of_job_id = case when repost_of_job_id in (:duplicate, :canonical) then null else repost_of_job_id end
				where id in (:duplicate, :canonical)
				""")
			.param("canonical", canonicalId)
			.param("duplicate", duplicateId)
			.update();
	}

	/**
	 * Separa a cópia da canônica.
	 * @return se a vaga era uma cópia
	 */
	public boolean unlink(long jobId) {
		return this.jdbc.sql("update job set duplicate_of_job_id = null where id = :id and duplicate_of_job_id is not null")
			.param("id", jobId)
			.update() > 0;
	}

	/**
	 * Fecha as vagas de outras fontes ligadas às que acabaram de fechar no ATS (a canônica de alerta, por exemplo).
	 * @return quantas vagas foram fechadas
	 */
	public int closeLinked(Collection<Long> closedIds, Instant now) {
		if (closedIds.isEmpty()) {
			return 0;
		}
		return this.jdbc.sql("""
				update job j set closed_at = :now
				where j.closed_at is null and j.source not in ('GREENHOUSE', 'LEVER', 'ASHBY')
				  and exists (select 1 from job c where c.id = any(cast(string_to_array(:ids, ',') as bigint[]))
				              and (c.duplicate_of_job_id = j.id or j.duplicate_of_job_id = c.id))
				""")
			.param("now", Timestamp.from(now))
			.param("ids", closedIds.stream().map(String::valueOf).collect(Collectors.joining(",")))
			.update();
	}

	/**
	 * A vaga recém-criada e a existente que é a mesma.
	 * @param existingEngaged a existente já tem candidatura ou avaliação sua
	 */
	public record Match(long newJobId, long existingJobId, JobOrigin newOrigin, JobOrigin existingOrigin,
			int newDescriptionLength, int existingDescriptionLength, boolean existingEngaged) {

		/**
		 * Fica como canônica a que tem candidatura ou avaliação; depois, a do ATS (lista tudo e fecha sozinha); depois,
		 * a de descrição maior. No empate, a que já existia.
		 */
		public long canonicalId() {
			if (this.existingEngaged) {
				return this.existingJobId;
			}
			if (this.newOrigin.fullListing() != this.existingOrigin.fullListing()) {
				return this.newOrigin.fullListing() ? this.newJobId : this.existingJobId;
			}
			return (this.newDescriptionLength > this.existingDescriptionLength) ? this.newJobId : this.existingJobId;
		}

		public long duplicateId() {
			return (canonicalId() == this.newJobId) ? this.existingJobId : this.newJobId;
		}

	}

}
