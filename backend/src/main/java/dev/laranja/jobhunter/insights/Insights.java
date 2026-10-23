package dev.laranja.jobhunter.insights;

import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import dev.laranja.jobhunter.insights.InsightReport.Funnel;
import dev.laranja.jobhunter.insights.InsightReport.Gap;
import dev.laranja.jobhunter.insights.InsightReport.Recommendations;
import dev.laranja.jobhunter.insights.InsightReport.ResumePerformance;
import dev.laranja.jobhunter.insights.InsightReport.SkillDemand;
import dev.laranja.jobhunter.insights.InsightReport.SuggestedFilter;
import dev.laranja.jobhunter.insights.internal.InsightEngine;
import dev.laranja.jobhunter.notifications.Notifications;
import dev.laranja.jobhunter.shared.jdbc.JdbcRows;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/** Gera e guarda o relatório semanal (domingo às 21h ou sob demanda) e expõe o último. */
@Service
public class Insights {

	private static final ZoneId ZONE = ZoneId.of("America/Sao_Paulo");

	private final InsightEngine engine;

	private final JdbcClient jdbc;

	private final JsonMapper json;

	private final Notifications notifications;

	private final Clock clock;

	Insights(InsightEngine engine, JdbcClient jdbc, JsonMapper json, Notifications notifications, Clock clock) {
		this.engine = engine;
		this.jdbc = jdbc;
		this.json = json;
		this.notifications = notifications;
		this.clock = clock;
	}

	@Scheduled(cron = "${jobhunter.insights.cron:0 0 21 * * SUN}", zone = "America/Sao_Paulo")
	@SchedulerLock(name = "weekly-insights", lockAtMostFor = "PT30M")
	void weekly() {
		InsightReport report = generate();
		this.notifications.notify("RELATORIO_SEMANAL", "Relatório semanal pronto",
				report.recommendations().diagnosisExplanation(), "/insights", "relatorio-" + report.periodEnd());
	}

	/** Calcula as agregações, pede as sugestões e grava o relatório. A chamada ao LLM fica fora de transação. */
	public InsightReport generate() {
		LocalDate end = LocalDate.now(this.clock.withZone(ZONE));
		LocalDate start = end.minusDays(27);
		InsightEngine.Draft draft = this.engine.compute(start.atStartOfDay(ZONE).toInstant(),
				this.clock.instant().minus(Duration.ofDays(90)), end.withDayOfMonth(1));
		long id = this.jdbc.sql("""
				insert into insight_report (period_start, period_end, top_skills, gaps, funnel, resume_performance,
				                            suggested_filters, ai_accuracy, llm_cost_usd, recommendations, model_version,
				                            created_at)
				values (:start, :end, cast(:topSkills as jsonb), cast(:gaps as jsonb), cast(:funnel as jsonb),
				        cast(:resumePerformance as jsonb), cast(:suggestedFilters as jsonb), :aiAccuracy, :llmCost,
				        cast(:recommendations as jsonb), :modelVersion, :now)
				returning id
				""")
			.param("start", Date.valueOf(start))
			.param("end", Date.valueOf(end))
			.param("topSkills", this.json.writeValueAsString(draft.topSkills()))
			.param("gaps", this.json.writeValueAsString(draft.gaps()))
			.param("funnel", this.json.writeValueAsString(draft.funnel()))
			.param("resumePerformance", this.json.writeValueAsString(draft.resumePerformance()))
			.param("suggestedFilters", this.json.writeValueAsString(draft.suggestedFilters()))
			.param("aiAccuracy", draft.aiAccuracy())
			.param("llmCost", draft.llmCostUsd())
			.param("recommendations", this.json.writeValueAsString(draft.recommendations()))
			.param("modelVersion", draft.modelVersion())
			.param("now", Timestamp.from(this.clock.instant()))
			.query(Long.class)
			.single();
		return latest().filter(report -> report.id() == id).orElseThrow();
	}

	public Optional<InsightReport> latest() {
		return this.jdbc.sql("select * from insight_report order by created_at desc, id desc limit 1")
			.query((rs, row) -> toReport(rs))
			.optional();
	}

	/** Uso da Claude API no mês corrente, por tarefa. */
	public List<LlmUsage> monthUsage() {
		return this.engine.usage(LocalDate.now(this.clock.withZone(ZONE)).withDayOfMonth(1));
	}

	private InsightReport toReport(ResultSet rs) throws SQLException {
		return new InsightReport(rs.getLong("id"), rs.getDate("period_start").toLocalDate(),
				rs.getDate("period_end").toLocalDate(),
				read(rs, "top_skills", new TypeReference<List<SkillDemand>>() {
				}), read(rs, "gaps", new TypeReference<List<Gap>>() {
				}), read(rs, "funnel", new TypeReference<Funnel>() {
				}), read(rs, "resume_performance", new TypeReference<List<ResumePerformance>>() {
				}), read(rs, "suggested_filters", new TypeReference<List<SuggestedFilter>>() {
				}), rs.getBigDecimal("ai_accuracy"), rs.getBigDecimal("llm_cost_usd"),
				read(rs, "recommendations", new TypeReference<Recommendations>() {
				}), rs.getString("model_version"), JdbcRows.instant(rs, "created_at"));
	}

	private <T> T read(ResultSet rs, String column, TypeReference<T> type) throws SQLException {
		return this.json.readValue(rs.getString(column), type);
	}

}
