package dev.laranja.jobhunter.companies.web;

import java.sql.Array;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import dev.laranja.jobhunter.companies.CompanyDirectory;
import dev.laranja.jobhunter.companies.CompanyKind;
import dev.laranja.jobhunter.companies.CompanyMonitoring;
import dev.laranja.jobhunter.companies.UpdateCompanyCommand;
import dev.laranja.jobhunter.companies.WatchStatus;
import dev.laranja.jobhunter.shared.NotFoundException;
import dev.laranja.jobhunter.shared.jdbc.JdbcRows;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.jspecify.annotations.Nullable;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/companies")
class CompanyController {

	private static final TypeReference<Map<String, Integer>> COUNTS = new TypeReference<>() {
	};

	private static final String SELECT = """
			select c.id, c.name, c.website, c.ats_type, c.ats_slug, c.kind, c.watch_status, c.notes, c.first_seen_at,
			       c.client_sectors, c.contract_types,
			       count(j.id) as jobs_total,
			       count(j.id) filter (where j.closed_at is null) as jobs_active,
			       count(j.id) filter (where j.first_seen_at > now() - interval '90 days') as jobs_last_90_days,
			       count(j.id) filter (where j.repost_of_job_id is not null) as reposts,
			       max(j.first_seen_at) as last_job_at,
			       round(avg(extract(epoch from (j.closed_at - j.first_seen_at)) / 86400)
			             filter (where j.closed_at is not null)::numeric, 1) as avg_days_open,
			       count(a.id) as applications
			from company c
			left join job j on j.company_id = c.id
			left join application a on a.job_id = j.id
			""";

	private final CompanyDirectory directory;

	private final CompanyMonitoring monitoring;

	private final JdbcClient jdbc;

	private final JsonMapper json;

	CompanyController(CompanyDirectory directory, CompanyMonitoring monitoring, JdbcClient jdbc, JsonMapper json) {
		this.directory = directory;
		this.monitoring = monitoring;
		this.jdbc = jdbc;
		this.json = json;
	}

	/**
	 * Empresas com as métricas de vagas.
	 * @param watch filtra pelo acompanhamento
	 */
	@GetMapping
	List<CompanyItem> list(@RequestParam(required = false) @Nullable WatchStatus watch,
			@RequestParam(required = false) @Nullable CompanyKind kind) {
		return this.jdbc.sql(SELECT + """
				where (cast(:watch as text) is null or c.watch_status = :watch)
				  and (cast(:kind as text) is null or c.kind = :kind)
				group by c.id
				order by last_job_at desc nulls last, c.name
				""")
			.param("watch", (watch != null) ? watch.name() : null, Types.VARCHAR)
			.param("kind", (kind != null) ? kind.name() : null, Types.VARCHAR)
			.query((rs, row) -> toItem(rs))
			.list();
	}

	@GetMapping("/{id}")
	CompanyItem get(@PathVariable long id) {
		return this.jdbc.sql(SELECT + " where c.id = :id group by c.id")
			.param("id", id)
			.query((rs, row) -> toItem(rs))
			.optional()
			.orElseThrow(() -> NotFoundException.of("Empresa", id));
	}

	/** Distribuições dos últimos 90 dias, a linha do tempo das vagas e as suas candidaturas na empresa. */
	@GetMapping("/{id}/overview")
	CompanyOverview overview(@PathVariable long id) {
		get(id);
		Map<String, Integer> seniority = counts("""
				select coalesce(a.seniority, 'NAO_INFORMADO') as label, count(*) as total
				from job j left join job_analysis a on a.job_id = j.id
				where j.company_id = :id and j.first_seen_at > now() - interval '90 days' group by 1
				""", id);
		Map<String, Integer> workModel = counts("""
				select j.work_model as label, count(*) as total from job j
				where j.company_id = :id and j.first_seen_at > now() - interval '90 days' group by 1
				""", id);
		List<SkillCount> skills = this.jdbc.sql("""
				select skill, count(*) as total from job j join job_analysis a on a.job_id = j.id,
				     jsonb_array_elements_text(a.required_skills) skill
				where j.company_id = :id and j.first_seen_at > now() - interval '90 days'
				group by skill order by total desc limit 10
				""")
			.param("id", id)
			.query((rs, row) -> new SkillCount(rs.getString("skill"), rs.getInt("total")))
			.list();
		List<CompanyJob> jobs = this.jdbc.sql("""
				select j.id, j.title, j.first_seen_at, j.closed_at, j.repost_of_job_id, a.status as application_status,
				       an.match_score
				from job j
				left join application a on a.job_id = j.id
				left join job_analysis an on an.job_id = j.id
				where j.company_id = :id
				order by j.first_seen_at desc
				limit 100
				""")
			.param("id", id)
			.query((rs, row) -> new CompanyJob(rs.getLong("id"), rs.getString("title"), JdbcRows.instant(rs, "first_seen_at"),
					JdbcRows.instant(rs, "closed_at"), JdbcRows.nullableLong(rs, "repost_of_job_id"),
					rs.getString("application_status"), (Integer) rs.getObject("match_score")))
			.list();
		return new CompanyOverview(seniority, workModel, skills, jobs);
	}

	@GetMapping("/{id}/metrics")
	List<WeeklyMetrics> metrics(@PathVariable long id,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) @Nullable LocalDate from,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) @Nullable LocalDate to) {
		return this.jdbc.sql("""
				select week_start, jobs_opened, jobs_closed, jobs_active, reposts, avg_days_open, headcount
				from company_metrics_weekly
				where company_id = :id and week_start >= :from and week_start <= :to
				order by week_start
				""")
			.param("id", id)
			.param("from", Date.valueOf((from != null) ? from : LocalDate.now().minusWeeks(26)))
			.param("to", Date.valueOf((to != null) ? to : LocalDate.now()))
			.query((rs, row) -> new WeeklyMetrics(rs.getDate("week_start").toLocalDate(), rs.getInt("jobs_opened"),
					rs.getInt("jobs_closed"), rs.getInt("jobs_active"), rs.getInt("reposts"),
					(rs.getBigDecimal("avg_days_open") != null) ? rs.getBigDecimal("avg_days_open").doubleValue() : null,
					(Integer) rs.getObject("headcount")))
			.list();
	}

	/** Número de funcionários (do LinkedIn), anotado na semana atual. */
	@PutMapping("/{id}/headcount")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void headcount(@PathVariable long id, @Valid @RequestBody Headcount headcount) {
		get(id);
		this.jdbc.sql("""
				insert into company_metrics_weekly (company_id, week_start, headcount)
				values (:id, date_trunc('week', now())::date, :headcount)
				on conflict (company_id, week_start) do update set headcount = excluded.headcount
				""")
			.param("id", id)
			.param("headcount", headcount.headcount())
			.update();
	}

	@PatchMapping("/{id}")
	CompanyItem update(@PathVariable long id, @Valid @RequestBody UpdateCompanyCommand command) {
		this.directory.update(id, command);
		return get(id);
	}

	@PostMapping("/{id}/merge")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void merge(@PathVariable long id, @Valid @RequestBody MergeCommand command) {
		this.directory.merge(id, command.targetId());
	}

	/** Recalcula as métricas semanais e os alertas agora (normalmente roda domingo à noite). */
	@PostMapping("/refresh-metrics")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void refresh() {
		this.monitoring.refresh();
	}

	private Map<String, Integer> counts(String sql, long id) {
		String jsonObject = this.jdbc.sql("select coalesce(jsonb_object_agg(label, total), '{}')::text from (" + sql + ") x")
			.param("id", id)
			.query(String.class)
			.single();
		return this.json.readValue(jsonObject, COUNTS);
	}

	private static CompanyItem toItem(ResultSet rs) throws SQLException {
		return new CompanyItem(rs.getLong("id"), rs.getString("name"), rs.getString("website"),
				rs.getString("ats_type"), rs.getString("ats_slug"), CompanyKind.valueOf(rs.getString("kind")),
				WatchStatus.valueOf(rs.getString("watch_status")), rs.getString("notes"),
				JdbcRows.instant(rs, "first_seen_at"), strings(rs.getArray("client_sectors")),
				strings(rs.getArray("contract_types")), rs.getLong("jobs_total"), rs.getLong("jobs_active"),
				rs.getLong("jobs_last_90_days"), rs.getLong("reposts"), JdbcRows.instant(rs, "last_job_at"),
				(rs.getBigDecimal("avg_days_open") != null) ? rs.getBigDecimal("avg_days_open").doubleValue() : null,
				rs.getLong("applications"));
	}

	private static List<String> strings(Array array) throws SQLException {
		return (array == null) ? List.of() : Arrays.asList((String[]) array.getArray());
	}

	/**
	 * Empresa com as métricas de vagas.
	 * @param avgDaysOpen tempo médio no ar das vagas já fechadas, em dias
	 */
	record CompanyItem(long id, String name, @Nullable String website, @Nullable String atsType,
			@Nullable String atsSlug, CompanyKind kind, WatchStatus watchStatus, @Nullable String notes,
			Instant firstSeenAt, List<String> clientSectors, List<String> contractTypes, long jobsTotal, long jobsActive,
			long jobsLast90Days, long reposts, @Nullable Instant lastJobAt, @Nullable Double avgDaysOpen,
			long applications) {
	}

	record CompanyOverview(Map<String, Integer> bySeniority, Map<String, Integer> byWorkModel, List<SkillCount> topSkills,
			List<CompanyJob> jobs) {
	}

	record SkillCount(String skill, int count) {
	}

	/** @param repostOfJobId vaga anterior que esta republica */
	record CompanyJob(long id, String title, Instant firstSeenAt, @Nullable Instant closedAt, @Nullable Long repostOfJobId,
			@Nullable String applicationStatus, @Nullable Integer matchScore) {
	}

	record WeeklyMetrics(LocalDate weekStart, int jobsOpened, int jobsClosed, int jobsActive, int reposts,
			@Nullable Double avgDaysOpen, @Nullable Integer headcount) {
	}

	record Headcount(@NotNull @Min(1) Integer headcount) {
	}

	/** Mescla esta empresa (origem) na empresa de destino. */
	@Schema(name = "MergeCompany")
	record MergeCommand(@NotNull Long targetId) {
	}

}
