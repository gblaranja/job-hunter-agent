package dev.laranja.jobhunter.companies;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Locale;

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Monitoramento das empresas: série semanal de vagas abertas, fechadas, ativas e republicadas, o tipo da empresa
 * deduzido das análises das vagas e os alertas. Roda todo domingo à noite; o histórico começa no primeiro dia de
 * coleta.
 */
@Service
public class CompanyMonitoring {

	private static final Logger log = LoggerFactory.getLogger(CompanyMonitoring.class);

	private static final ZoneId ZONE = ZoneId.of("America/Sao_Paulo");

	private final JdbcClient jdbc;

	private final ApplicationEventPublisher events;

	private final Clock clock;

	CompanyMonitoring(JdbcClient jdbc, ApplicationEventPublisher events, Clock clock) {
		this.jdbc = jdbc;
		this.events = events;
		this.clock = clock;
	}

	@Scheduled(cron = "${jobhunter.companies.metrics-cron:0 0 22 * * SUN}", zone = "America/Sao_Paulo")
	@SchedulerLock(name = "company-metrics", lockAtMostFor = "PT1H")
	void weekly() {
		refresh();
	}

	/** Recalcula tudo e avalia os alertas; também disparado pelas Configurações. */
	@Transactional
	public int refresh() {
		refreshProfiles();
		int rows = recomputeMetrics();
		int alerts = evaluateAlerts();
		log.info("Métricas semanais recalculadas ({} linhas); {} alertas.", rows, alerts);
		return rows;
	}

	/** Empresas monitoradas ou alvo com ATS público configurado. */
	@Transactional(readOnly = true)
	public List<TrackedCompany> tracked(String atsType) {
		return this.jdbc.sql("""
				select id, name, ats_type, ats_slug from company
				where watch_status in ('MONITORADA', 'ALVO') and upper(ats_type) = :ats and ats_slug is not null
				order by name
				""")
			.param("ats", atsType.toUpperCase(Locale.ROOT))
			.query(TrackedCompany.class)
			.list();
	}

	/**
	 * Tipo da empresa pela maioria das análises das vagas (agências e consultorias se revelam nas descrições),
	 * setores dos clientes citados e tipos de contrato oferecidos.
	 */
	void refreshProfiles() {
		this.jdbc.sql("""
				update company c set
				    kind = coalesce((
				        select a.employer_kind from job j join job_analysis a on a.job_id = j.id
				        where j.company_id = c.id and a.employer_kind in ('CONTRATANTE', 'CONSULTORIA_TI', 'RECRUTAMENTO')
				        group by a.employer_kind order by count(*) desc limit 1), c.kind),
				    client_sectors = coalesce((
				        -- alias explícito: "sector" sozinho resolveria para a coluna company.sector
				        select array_agg(distinct cs.value) from job j join job_analysis a on a.job_id = j.id,
				               jsonb_array_elements_text(a.client_sectors) as cs(value)
				        where j.company_id = c.id), '{}'),
				    contract_types = coalesce((
				        select array_agg(distinct a.contract_type) from job j join job_analysis a on a.job_id = j.id
				        where j.company_id = c.id and a.contract_type in ('CLT', 'PJ')), '{}')
				""").update();
	}

	int recomputeMetrics() {
		int rows = this.jdbc.sql("""
				insert into company_metrics_weekly (company_id, week_start, jobs_opened, jobs_closed, jobs_active, reposts,
				                                    avg_days_open)
				select c.id, w.week_start,
				       count(j.id) filter (where date_trunc('week', j.first_seen_at)::date = w.week_start),
				       count(j.id) filter (where date_trunc('week', j.closed_at)::date = w.week_start),
				       count(j.id) filter (where j.first_seen_at < w.week_start + 7
				                           and (j.closed_at is null or j.closed_at >= w.week_start)),
				       count(j.id) filter (where j.repost_of_job_id is not null
				                           and date_trunc('week', j.first_seen_at)::date = w.week_start),
				       round(avg(extract(epoch from (j.closed_at - j.first_seen_at)) / 86400)
				             filter (where date_trunc('week', j.closed_at)::date = w.week_start)::numeric, 1)
				from company c
				cross join lateral (
				    select generate_series(date_trunc('week', min(first_seen_at)), date_trunc('week', now()),
				                           interval '1 week')::date as week_start
				    from job where company_id = c.id) w
				left join job j on j.company_id = c.id
				-- vagas de agência não distorcem os números de quem contrata
				where c.kind <> 'RECRUTAMENTO' and w.week_start is not null
				group by c.id, w.week_start
				on conflict (company_id, week_start) do update set
				    jobs_opened = excluded.jobs_opened, jobs_closed = excluded.jobs_closed,
				    jobs_active = excluded.jobs_active, reposts = excluded.reposts, avg_days_open = excluded.avg_days_open
				""").update();
		this.jdbc.sql("""
				update company_metrics_weekly m set
				    by_seniority = coalesce((
				        select jsonb_object_agg(level, total) from (
				            select coalesce(a.seniority, 'NAO_INFORMADO') as level, count(*) as total
				            from job j left join job_analysis a on a.job_id = j.id
				            where j.company_id = m.company_id and date_trunc('week', j.first_seen_at)::date = m.week_start
				            group by 1) x), '{}'),
				    by_work_model = coalesce((
				        select jsonb_object_agg(model, total) from (
				            select j.work_model as model, count(*) as total from job j
				            where j.company_id = m.company_id and date_trunc('week', j.first_seen_at)::date = m.week_start
				            group by 1) x), '{}'),
				    top_skills = coalesce((
				        select jsonb_agg(skill order by total desc) from (
				            select skill, count(*) as total
				            from job j join job_analysis a on a.job_id = j.id,
				                 jsonb_array_elements_text(a.required_skills) skill
				            where j.company_id = m.company_id and date_trunc('week', j.first_seen_at)::date = m.week_start
				            group by skill order by total desc limit 5) x), '[]')
				where m.week_start >= current_date - 120
				""").update();
		return rows;
	}

	/** Alertas da semana; a chave de deduplicação evita repetir o mesmo aviso. */
	int evaluateAlerts() {
		LocalDate week = LocalDate.now(this.clock.withZone(ZONE)).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
		int raised = 0;
		record NewJob(long companyId, String company, long jobId, String title) {
		}
		for (NewJob job : this.jdbc.sql("""
				select c.id as company_id, c.name as company, j.id as job_id, j.title from job j
				join company c on c.id = j.company_id
				where c.watch_status = 'ALVO' and j.first_seen_at >= :since
				""").param("since", Timestamp.from(this.clock.instant().minusSeconds(7 * 86400))).query(NewJob.class).list()) {
			raise(new CompanyAlertRaised(job.companyId(), "EMPRESA_ALVO_VAGA", job.company() + " abriu uma vaga",
					job.title(), "alvo-vaga-" + job.jobId()));
			raised++;
		}
		record Company(long id, String name) {
		}
		for (Company company : this.jdbc.sql("""
				select c.id, c.name from company c
				where exists (select 1 from job j where j.company_id = c.id and j.first_seen_at >= cast(:weekStart as date))
				  and not exists (select 1 from job j where j.company_id = c.id
				                  and j.first_seen_at < cast(:weekStart as date) and j.first_seen_at >= cast(:weekStart as date) - interval '60 days')
				  and exists (select 1 from job j where j.company_id = c.id and j.first_seen_at < cast(:weekStart as date) - interval '60 days')
				""").param("weekStart", Date.valueOf(week)).query(Company.class).list()) {
			raise(new CompanyAlertRaised(company.id(), "EMPRESA_VOLTOU", company.name() + " voltou a contratar",
					"Primeira vaga depois de mais de 60 dias sem abrir nenhuma.", "voltou-" + company.id() + "-" + week));
			raised++;
		}
		for (Company company : this.jdbc.sql("""
				select c.id, c.name from company c join company_metrics_weekly m on m.company_id = c.id
				where c.watch_status in ('MONITORADA', 'ALVO')
				group by c.id, c.name
				having sum(m.jobs_opened) filter (where m.week_start between cast(:weekStart as date) - 182 and cast(:weekStart as date) - 91) >= 4
				   and sum(m.jobs_opened) filter (where m.week_start > cast(:weekStart as date) - 91)
				       <= 0.5 * sum(m.jobs_opened) filter (where m.week_start between cast(:weekStart as date) - 182 and cast(:weekStart as date) - 91)
				""").param("weekStart", Date.valueOf(week)).query(Company.class).list()) {
			raise(new CompanyAlertRaised(company.id(), "VOLUME_CAIU", "Vagas de " + company.name() + " caíram pela metade",
					"O último trimestre teve metade ou menos das vagas do anterior.", "queda-" + company.id() + "-" + week));
			raised++;
		}
		return raised;
	}

	private void raise(CompanyAlertRaised alert) {
		this.events.publishEvent(alert);
	}

}
