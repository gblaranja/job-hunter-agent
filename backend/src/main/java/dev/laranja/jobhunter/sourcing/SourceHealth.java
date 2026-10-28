package dev.laranja.jobhunter.sourcing;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import dev.laranja.jobhunter.notifications.Notifications;
import dev.laranja.jobhunter.shared.jdbc.JdbcRows;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.jspecify.annotations.Nullable;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Saúde da coleta: quando cada fonte trouxe vaga nova pela última vez, os erros recentes e os alertas de vaga que
 * não viraram vaga (em geral, o remetente mudou o HTML e o parser precisa de ajuste). Avisa uma vez por dia quando
 * algo quebrou, para a coleta não falhar em silêncio.
 */
@Service
public class SourceHealth {

	/** Coletas seguidas com erro que viram aviso. */
	static final int FAILING_RUNS = 3;

	private static final ZoneId ZONE = ZoneId.of("America/Sao_Paulo");

	private final List<JobSource> sources;

	private final JdbcClient jdbc;

	private final Notifications notifications;

	private final Clock clock;

	SourceHealth(List<JobSource> sources, JdbcClient jdbc, Notifications notifications, Clock clock) {
		this.sources = sources;
		this.jdbc = jdbc;
		this.notifications = notifications;
		this.clock = clock;
	}

	public HealthReport report() {
		Instant now = this.clock.instant();
		Map<String, List<RunStats>> runs = runsBySource(now.minus(Duration.ofDays(30)));
		Map<String, Boolean> available = new LinkedHashMap<>();
		this.sources.forEach(source -> available.put(source.id(), source.available()));
		runs.keySet().forEach(id -> available.putIfAbsent(id, false));
		Instant week = now.minus(Duration.ofDays(7));
		List<SourceStatus> statuses = available.entrySet().stream().map(entry -> {
			List<RunStats> recent = runs.getOrDefault(entry.getKey(), List.of());
			return new SourceStatus(entry.getKey(), entry.getValue(),
					recent.isEmpty() ? null : recent.getFirst().startedAt(),
					recent.stream().filter(run -> run.created() > 0).map(RunStats::startedAt).findFirst().orElse(null),
					recent.stream().filter(run -> run.startedAt().isAfter(week)).mapToInt(RunStats::created).sum(),
					recent.stream().filter(run -> run.startedAt().isAfter(week)).mapToInt(RunStats::errors).sum(),
					failingInRow(recent));
		}).toList();
		return new HealthReport(statuses, unrecognizedAlerts(now.minus(Duration.ofDays(14))));
	}

	/** Verificação diária: alertas não reconhecidos nas últimas 24 horas e fontes falhando em seguida. */
	@Scheduled(cron = "${jobhunter.sourcing.health-cron:0 0 9 * * *}", zone = "America/Sao_Paulo")
	@SchedulerLock(name = "source-health", lockAtMostFor = "PT10M")
	public void check() {
		Instant now = this.clock.instant();
		LocalDate today = LocalDate.ofInstant(now, ZONE);
		List<UnrecognizedAlert> alerts = unrecognizedAlerts(now.minus(Duration.ofDays(1)));
		if (!alerts.isEmpty()) {
			String senders = alerts.stream().map(UnrecognizedAlert::sender).distinct().collect(Collectors.joining(", "));
			this.notifications.notify("FONTE_COM_PROBLEMA",
					(alerts.size() == 1) ? "1 alerta de vaga não virou vaga" : alerts.size() + " alertas de vaga não viraram vaga",
					"De " + senders + ". O formato do e-mail pode ter mudado; os detalhes estão em Configurações.",
					"/settings", "alertas-nao-reconhecidos-" + today);
		}
		for (SourceStatus status : report().sources()) {
			if (status.available() && status.failingRunsInRow() >= FAILING_RUNS) {
				this.notifications.notify("FONTE_COM_PROBLEMA",
						"A fonte " + status.source() + " falhou nas últimas " + status.failingRunsInRow() + " coletas", null,
						"/settings", "fonte-com-erro-" + status.source() + "-" + today);
			}
		}
	}

	private Map<String, List<RunStats>> runsBySource(Instant since) {
		Map<String, List<RunStats>> result = new LinkedHashMap<>();
		this.jdbc.sql("""
				select s.key as source, r.started_at, coalesce((s.value ->> 'created')::int, 0) as created,
				       coalesce((s.value ->> 'errors')::int, 0) as errors
				from collection_run r, jsonb_each(r.stats) s
				where r.started_at >= :since
				order by r.started_at desc, r.id desc
				""")
			.param("since", Timestamp.from(since))
			.query((rs, row) -> new RunStats(rs.getString("source"), JdbcRows.instant(rs, "started_at"),
					rs.getInt("created"), rs.getInt("errors")))
			.list()
			.forEach(run -> result.computeIfAbsent(run.source(), key -> new ArrayList<>()).add(run));
		return result;
	}

	private static int failingInRow(List<RunStats> recent) {
		int count = 0;
		for (RunStats run : recent) {
			if (run.errors() == 0) {
				break;
			}
			count++;
		}
		return count;
	}

	/** Alertas lidos que não geraram nenhuma vaga (o corpo do e-mail não é guardado; o Gmail tem o original). */
	private List<UnrecognizedAlert> unrecognizedAlerts(Instant since) {
		return this.jdbc.sql("""
				select id, gmail_id, sender, subject, received_at from email_message
				where kind = 'ALERTA' and parsed_at >= :since and jobs_found = 0
				order by received_at desc
				limit 50
				""")
			.param("since", Timestamp.from(since))
			.query((rs, row) -> new UnrecognizedAlert(rs.getLong("id"), rs.getString("gmail_id"), rs.getString("sender"),
					rs.getString("subject"), JdbcRows.instant(rs, "received_at")))
			.list();
	}

	private record RunStats(String source, Instant startedAt, int created, int errors) {
	}

	public record HealthReport(List<SourceStatus> sources, List<UnrecognizedAlert> unrecognizedAlerts) {
	}

	/**
	 * @param lastRunAt última coleta, nos últimos 30 dias, em que a fonte rodou
	 * @param lastNewJobAt última coleta, nos últimos 30 dias, que trouxe vaga nova
	 * @param failingRunsInRow coletas seguidas, da mais recente para trás, com erro
	 */
	public record SourceStatus(String source, boolean available, @Nullable Instant lastRunAt,
			@Nullable Instant lastNewJobAt, int newJobsLast7Days, int errorsLast7Days, int failingRunsInRow) {
	}

	public record UnrecognizedAlert(long emailId, String gmailId, String sender, @Nullable String subject,
			Instant receivedAt) {
	}

}
