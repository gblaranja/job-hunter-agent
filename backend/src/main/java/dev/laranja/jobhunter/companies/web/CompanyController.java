package dev.laranja.jobhunter.companies.web;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;

import dev.laranja.jobhunter.companies.CompanyDirectory;
import dev.laranja.jobhunter.companies.CompanyKind;
import dev.laranja.jobhunter.companies.UpdateCompanyCommand;
import dev.laranja.jobhunter.companies.WatchStatus;
import dev.laranja.jobhunter.shared.NotFoundException;
import dev.laranja.jobhunter.shared.jdbc.JdbcRows;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.jspecify.annotations.Nullable;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/companies")
class CompanyController {

	private static final String SELECT = """
			select c.id, c.name, c.website, c.ats_type, c.ats_slug, c.kind, c.watch_status, c.notes, c.first_seen_at,
			       count(j.id) as jobs_total,
			       count(j.id) filter (where j.first_seen_at > now() - interval '90 days') as jobs_last_90_days,
			       max(j.first_seen_at) as last_job_at,
			       count(a.id) as applications
			from company c
			left join job j on j.company_id = c.id
			left join application a on a.job_id = j.id
			""";

	private final CompanyDirectory directory;

	private final JdbcClient jdbc;

	CompanyController(CompanyDirectory directory, JdbcClient jdbc) {
		this.directory = directory;
		this.jdbc = jdbc;
	}

	@GetMapping
	List<CompanyItem> list() {
		return this.jdbc.sql(SELECT + " group by c.id order by last_job_at desc nulls last, c.name")
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

	private static CompanyItem toItem(ResultSet rs) throws SQLException {
		return new CompanyItem(rs.getLong("id"), rs.getString("name"), rs.getString("website"),
				rs.getString("ats_type"), rs.getString("ats_slug"), CompanyKind.valueOf(rs.getString("kind")),
				WatchStatus.valueOf(rs.getString("watch_status")), rs.getString("notes"),
				JdbcRows.instant(rs, "first_seen_at"), rs.getLong("jobs_total"), rs.getLong("jobs_last_90_days"),
				JdbcRows.instant(rs, "last_job_at"), rs.getLong("applications"));
	}

	record CompanyItem(long id, String name, @Nullable String website, @Nullable String atsType,
			@Nullable String atsSlug, CompanyKind kind, WatchStatus watchStatus, @Nullable String notes,
			Instant firstSeenAt, long jobsTotal, long jobsLast90Days, @Nullable Instant lastJobAt,
			long applications) {
	}

	/** Mescla esta empresa (origem) na empresa de destino. */
	@Schema(name = "MergeCompany")
	record MergeCommand(@NotNull Long targetId) {
	}

}
