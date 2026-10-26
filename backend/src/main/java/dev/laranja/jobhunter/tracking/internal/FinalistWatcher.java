package dev.laranja.jobhunter.tracking.internal;

import java.sql.Timestamp;
import java.time.Clock;
import java.util.List;
import java.util.Optional;

import dev.laranja.jobhunter.jobs.JobCollected;
import dev.laranja.jobhunter.tracking.FinalistJobOpened;
import dev.laranja.jobhunter.tracking.TrackingProperties;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/**
 * Volta às empresas onde você foi finalista: quando uma delas abre uma vaga com título parecido (similaridade acima
 * de {@value #MIN_SIMILARITY}) depois do intervalo configurado desde a recusa, publica {@link FinalistJobOpened}.
 */
@Component
class FinalistWatcher {

	static final double MIN_SIMILARITY = 0.5;

	private final JdbcClient jdbc;

	private final ApplicationEventPublisher events;

	private final TrackingProperties properties;

	private final Clock clock;

	FinalistWatcher(JdbcClient jdbc, ApplicationEventPublisher events, TrackingProperties properties, Clock clock) {
		this.jdbc = jdbc;
		this.events = events;
		this.properties = properties;
		this.clock = clock;
	}

	@ApplicationModuleListener
	void on(JobCollected event) {
		find(event.jobId()).ifPresent(this.events::publishEvent);
	}

	Optional<FinalistJobOpened> find(long jobId) {
		record Match(long applicationId, long previousJobId, String previousTitle, String title, String company) {
		}
		Optional<Match> match = this.jdbc.sql("""
				select a.id as application_id, previous.id as previous_job_id, previous.title as previous_title,
				       j.title, j.company
				from job j
				join job previous on previous.company_id = j.company_id and previous.id <> j.id
				join application a on a.job_id = previous.id
				where j.id = :jobId and a.status = 'RECUSADA'
				  and exists (select 1 from application_event e where e.application_id = a.id
				              and e.to_status in ('ENTREVISTA', 'TESTE_TECNICO', 'OFERTA'))
				  and (select max(e.created_at) from application_event e
				       where e.application_id = a.id and e.to_status = 'RECUSADA') <= :limit
				  and similarity(lower(previous.title), lower(j.title)) > :similarity
				  and not exists (select 1 from application current where current.job_id = j.id)
				order by similarity(lower(previous.title), lower(j.title)) desc, a.id desc
				limit 1
				""")
			.param("jobId", jobId)
			.param("limit", Timestamp.from(this.clock.instant().minus(this.properties.finalistCooldown())))
			.param("similarity", MIN_SIMILARITY)
			.query((rs, row) -> new Match(rs.getLong("application_id"), rs.getLong("previous_job_id"),
					rs.getString("previous_title"), rs.getString("title"), rs.getString("company")))
			.optional();
		return match.map(found -> new FinalistJobOpened(jobId, found.title(), found.company(), found.applicationId(),
				found.previousJobId(), found.previousTitle(), contacts(found.applicationId())));
	}

	// Leitura das conversas registradas no network, direto no SQL, para o tracking não depender do módulo.
	private List<String> contacts(long applicationId) {
		return this.jdbc.sql("""
				select distinct c.name from contact_interaction i join contact c on c.id = i.contact_id
				where i.application_id = :id order by c.name
				""")
			.param("id", applicationId)
			.query(String.class)
			.list();
	}

}
