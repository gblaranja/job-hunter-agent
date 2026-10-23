package dev.laranja.jobhunter.sourcing.web;

import java.sql.Types;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import dev.laranja.jobhunter.shared.jdbc.JdbcRows;
import dev.laranja.jobhunter.sourcing.JobCollector;
import org.jspecify.annotations.Nullable;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin")
class CollectionController {

	private static final TypeReference<Map<String, SourceCounts>> STATS = new TypeReference<>() {
	};

	private final JobCollector collector;

	private final JdbcClient jdbc;

	private final JsonMapper json;

	CollectionController(JobCollector collector, JdbcClient jdbc, JsonMapper json) {
		this.collector = collector;
		this.jdbc = jdbc;
		this.json = json;
	}

	/** Dispara a coleta agora e devolve o resultado (síncrono; leva segundos no volume de uso pessoal). */
	@PostMapping("/collect")
	CollectionRun collect() {
		long runId = this.collector.collect(JobCollector.Trigger.MANUAL);
		return runs(1, runId).getFirst();
	}

	@GetMapping("/collection-runs")
	List<CollectionRun> recentRuns() {
		return runs(20, null);
	}

	private List<CollectionRun> runs(int limit, @Nullable Long id) {
		return this.jdbc.sql("""
				select id, trigger, status, started_at, finished_at, stats::text as stats, error
				from collection_run
				where cast(:id as bigint) is null or id = :id
				order by started_at desc
				limit :limit
				""")
			.param("id", id, Types.BIGINT)
			.param("limit", limit)
			.query((rs, row) -> new CollectionRun(rs.getLong("id"), rs.getString("trigger"), rs.getString("status"),
					JdbcRows.instant(rs, "started_at"), JdbcRows.instant(rs, "finished_at"),
					this.json.readValue(rs.getString("stats"), STATS), rs.getString("error")))
			.list();
	}

	record CollectionRun(long id, String trigger, String status, Instant startedAt, @Nullable Instant finishedAt,
			Map<String, SourceCounts> stats, @Nullable String error) {
	}

	record SourceCounts(int created, int duplicate, int excluded, int errors) {
	}

}
