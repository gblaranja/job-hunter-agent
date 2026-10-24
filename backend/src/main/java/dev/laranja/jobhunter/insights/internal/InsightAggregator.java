package dev.laranja.jobhunter.insights.internal;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import dev.laranja.jobhunter.insights.FunnelBreakdown;
import dev.laranja.jobhunter.insights.InsightReport.Conversion;
import dev.laranja.jobhunter.insights.InsightReport.Funnel;
import dev.laranja.jobhunter.insights.InsightReport.Gap;
import dev.laranja.jobhunter.insights.InsightReport.ResumePerformance;
import dev.laranja.jobhunter.insights.InsightReport.SkillDemand;
import dev.laranja.jobhunter.insights.InsightReport.SuggestedFilter;
import dev.laranja.jobhunter.insights.LlmUsage;
import dev.laranja.jobhunter.shared.jdbc.JdbcRows;
import org.jspecify.annotations.Nullable;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/** Os números do relatório, todos por SQL sobre as vagas, análises, candidaturas, feedbacks e e-mails. */
@Component
class InsightAggregator {

	/** Quantos descartes pelo mesmo motivo bastam para sugerir um ajuste no filtro. */
	static final int SUGGESTION_THRESHOLD = 5;

	/** Quantas vagas do topo do ranking entram na qualidade do ranking. */
	static final int RANKING_TOP = 20;

	private static final Map<String, String> SENIORITY_KEYWORDS = Map.of("ESTAGIO", "estágio", "JUNIOR", "júnior",
			"PLENO", "pleno", "SENIOR", "sênior", "ESPECIALISTA", "especialista");

	private final JdbcClient jdbc;

	InsightAggregator(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	List<SkillDemand> topSkills(Instant since, Set<String> resumeSkills) {
		return this.jdbc.sql("""
				select skill, count(*) as total
				from job j join job_analysis a on a.job_id = j.id, jsonb_array_elements_text(a.required_skills) skill
				where j.first_seen_at >= :since
				group by skill order by total desc, skill limit 15
				""")
			.param("since", Timestamp.from(since))
			.query((rs, row) -> new SkillDemand(rs.getString("skill"), rs.getInt("total"),
					resumeSkills.isEmpty() ? null : resumeSkills.contains(rs.getString("skill").toLowerCase(Locale.ROOT))))
			.list();
	}

	List<Gap> gaps(Instant since) {
		return this.jdbc.sql("""
				select gap, count(*) as total, round(avg(a.match_score)) as avg_score
				from job j join job_analysis a on a.job_id = j.id, jsonb_array_elements_text(a.gaps) gap
				where j.first_seen_at >= :since
				group by gap order by total desc, gap limit 10
				""")
			.param("since", Timestamp.from(since))
			.query((rs, row) -> new Gap(rs.getString("gap"), rs.getInt("total"),
					(rs.getObject("avg_score") != null) ? rs.getInt("avg_score") : null))
			.list();
	}

	/** Etapa mais avançada que cada candidatura já alcançou, mesmo que depois tenha sido recusada. */
	private List<Reached> reached() {
		return this.jdbc.sql("""
				select a.id, a.resume_version_id, r.label as resume_label, an.seniority, an.match_score,
				       bool_or(e.to_status in ('APLICADA', 'TRIAGEM', 'ENTREVISTA', 'TESTE_TECNICO', 'OFERTA')) as applied,
				       bool_or(e.to_status in ('TRIAGEM', 'ENTREVISTA', 'TESTE_TECNICO', 'OFERTA')) as screening,
				       bool_or(e.to_status in ('ENTREVISTA', 'TESTE_TECNICO', 'OFERTA')) as interview,
				       bool_or(e.to_status = 'OFERTA') as offer
				from application a
				join application_event e on e.application_id = a.id
				left join resume_version r on r.id = a.resume_version_id
				left join job_analysis an on an.job_id = a.job_id
				group by a.id, r.label, an.seniority, an.match_score
				""")
			.query((rs, row) -> new Reached((Long) rs.getObject("resume_version_id"), rs.getString("resume_label"),
					rs.getString("seniority"), (Integer) rs.getObject("match_score"), rs.getBoolean("applied"),
					rs.getBoolean("screening"), rs.getBoolean("interview"), rs.getBoolean("offer")))
			.list();
	}

	Funnel funnel() {
		List<Reached> applied = reached().stream().filter(Reached::applied).toList();
		List<Integer> scores = applied.stream().map(Reached::score).filter(score -> score != null).toList();
		Map<String, List<Reached>> bySeniority = applied.stream()
			.collect(Collectors.groupingBy(r -> (r.seniority() != null) ? r.seniority() : "NAO_INFORMADO",
					LinkedHashMap::new, Collectors.toList()));
		List<Conversion> conversions = bySeniority.entrySet()
			.stream()
			.map(entry -> new Conversion(entry.getKey(), entry.getValue().size(),
					(int) entry.getValue().stream().filter(Reached::interview).count()))
			.sorted((a, b) -> Integer.compare(b.applied(), a.applied()))
			.toList();
		Map<String, Integer> scoreByRating = new LinkedHashMap<>();
		this.jdbc.sql("""
				select f.rating, round(avg(a.match_score)) as avg_score
				from job_feedback f join job_analysis a on a.job_id = f.job_id
				where a.match_score is not null group by f.rating
				""").query((rs, row) -> scoreByRating.put(rs.getString("rating"), rs.getInt("avg_score"))).list();
		return new Funnel(applied.size(), count(applied, Reached::screening), count(applied, Reached::interview),
				count(applied, Reached::offer),
				scores.isEmpty() ? null : (int) Math.round(scores.stream().mapToInt(Integer::intValue).average().orElse(0)),
				conversions, scoreByRating);
	}

	List<ResumePerformance> resumePerformance() {
		Map<Long, List<Reached>> byResume = reached().stream()
			.filter(r -> r.applied() && r.resumeVersionId() != null)
			.collect(Collectors.groupingBy(Reached::resumeVersionId, LinkedHashMap::new, Collectors.toList()));
		return byResume.entrySet()
			.stream()
			.map(entry -> new ResumePerformance(entry.getKey(), entry.getValue().getFirst().resumeLabel(),
					entry.getValue().size(), count(entry.getValue(), Reached::interview)))
			.sorted((a, b) -> Integer.compare(b.applied(), a.applied()))
			.toList();
	}

	/** Funil aberto por fonte, canal, tempo até aplicar e adaptação do currículo, mais a qualidade do ranking. */
	FunnelBreakdown breakdown() {
		List<Applied> rows = this.jdbc.sql("""
				select a.id, j.source, a.channel, a.applied_at, j.first_seen_at,
				       exists (select 1 from tailored_resume t where t.resume_version_id = a.resume_version_id) as tailored,
				       bool_or(e.to_status in ('ENTREVISTA', 'TESTE_TECNICO', 'OFERTA')) as interview,
				       bool_or(e.to_status = 'OFERTA') as offer,
				       min(e.created_at) filter (where e.response_type is not null) as first_response_at
				from application a
				join job j on j.id = a.job_id
				join application_event e on e.application_id = a.id
				group by a.id, j.source, a.channel, a.applied_at, j.first_seen_at
				having bool_or(e.to_status in ('APLICADA', 'TRIAGEM', 'ENTREVISTA', 'TESTE_TECNICO', 'OFERTA'))
				""")
			.query((rs, row) -> new Applied(rs.getString("source"), rs.getString("channel"),
					JdbcRows.instant(rs, "applied_at"), JdbcRows.instant(rs, "first_seen_at"), rs.getBoolean("tailored"),
					rs.getBoolean("interview"), rs.getBoolean("offer"), JdbcRows.instant(rs, "first_response_at")))
			.list();
		List<Double> responseDays = rows.stream()
			.filter(row -> row.appliedAt() != null && row.firstResponseAt() != null
					&& !row.firstResponseAt().isBefore(row.appliedAt()))
			.map(row -> Duration.between(row.appliedAt(), row.firstResponseAt()).toMinutes() / 1440.0)
			.sorted()
			.toList();
		return new FunnelBreakdown(rows.size(), (int) rows.stream().filter(Applied::interview).count(),
				(int) rows.stream().filter(Applied::offer).count(), groups(rows, Applied::source),
				groups(rows, row -> (row.channel() != null) ? row.channel() : "NAO_INFORMADO"),
				groups(rows, row -> timeToApplyBucket(row.source(), row.firstSeenAt(), row.appliedAt())),
				groups(rows, row -> row.tailored() ? "ADAPTADO" : "PADRAO"), median(responseDays), rankingQuality());
	}

	/**
	 * Faixa do tempo entre a vaga aparecer no agente e você aplicar. Vagas manuais não contam (você costuma cadastrar
	 * depois de aplicar), nem datas incoerentes.
	 */
	static String timeToApplyBucket(String source, @Nullable Instant firstSeenAt, @Nullable Instant appliedAt) {
		if ("MANUAL".equals(source) || firstSeenAt == null || appliedAt == null || appliedAt.isBefore(firstSeenAt)) {
			return "SEM_DADO";
		}
		long hours = Duration.between(firstSeenAt, appliedAt).toHours();
		if (hours < 24) {
			return "ATE_1_DIA";
		}
		if (hours < 72) {
			return "1_A_3_DIAS";
		}
		return (hours < 168) ? "4_A_7_DIAS" : "MAIS_DE_7_DIAS";
	}

	private static List<FunnelBreakdown.FunnelGroup> groups(List<Applied> rows, Function<Applied, String> key) {
		Map<String, List<Applied>> grouped = rows.stream()
			.collect(Collectors.groupingBy(key, LinkedHashMap::new, Collectors.toList()));
		return grouped.entrySet()
			.stream()
			.map(entry -> new FunnelBreakdown.FunnelGroup(entry.getKey(), entry.getValue().size(),
					(int) entry.getValue().stream().filter(Applied::interview).count(),
					(int) entry.getValue().stream().filter(Applied::offer).count()))
			.sorted((a, b) -> Integer.compare(b.applied(), a.applied()))
			.toList();
	}

	private static @Nullable Double median(List<Double> sorted) {
		if (sorted.isEmpty()) {
			return null;
		}
		int middle = sorted.size() / 2;
		double value = (sorted.size() % 2 == 1) ? sorted.get(middle) : (sorted.get(middle - 1) + sorted.get(middle)) / 2;
		return Math.round(value * 10) / 10.0;
	}

	/**
	 * Entre as vagas que você já avaliou, as {@value #RANKING_TOP} de maior score e quantas eram as que você quis
	 * (candidatura ou "gostei"); as demais avaliadas são as descartadas ou com "não gostei".
	 */
	FunnelBreakdown.RankingQuality rankingQuality() {
		record Decided(int score, boolean positive) {
		}
		List<Decided> decided = this.jdbc.sql("""
				select an.match_score, (a.id is not null or f.rating = 'GOSTEI') as positive
				from job j
				join job_analysis an on an.job_id = j.id
				left join application a on a.job_id = j.id
				left join job_feedback f on f.job_id = j.id
				left join job_dismissal d on d.job_id = j.id
				where an.match_score is not null
				  and (a.id is not null or f.rating in ('GOSTEI', 'NAO_GOSTEI') or d.job_id is not null)
				order by an.match_score desc, j.id
				""")
			.query((rs, row) -> new Decided(rs.getInt("match_score"), rs.getBoolean("positive")))
			.list();
		List<Decided> top = decided.stream().limit(RANKING_TOP).toList();
		return new FunnelBreakdown.RankingQuality(decided.size(), (int) decided.stream().filter(Decided::positive).count(),
				top.size(), (int) top.stream().filter(Decided::positive).count(),
				average(decided.stream().filter(Decided::positive).map(Decided::score).toList()),
				average(decided.stream().filter(d -> !d.positive()).map(Decided::score).toList()));
	}

	private static @Nullable Integer average(List<Integer> values) {
		return values.isEmpty() ? null
				: (int) Math.round(values.stream().mapToInt(Integer::intValue).average().orElse(0));
	}

	/**
	 * Regras determinísticas sobre os motivos de "não gostei": a partir de {@value #SUGGESTION_THRESHOLD} descartes
	 * pelo mesmo motivo, sugere o ajuste correspondente no perfil de busca.
	 */
	List<SuggestedFilter> suggestedFilters(Instant since, Set<String> resumeSkills) {
		Map<String, Integer> reasons = new LinkedHashMap<>();
		this.jdbc.sql("""
				select reason, count(*) as total from job_feedback f, unnest(f.reasons) reason
				where f.rating = 'NAO_GOSTEI' and f.updated_at >= :since
				group by reason order by total desc
				""")
			.param("since", Timestamp.from(since))
			.query((rs, row) -> reasons.put(rs.getString("reason"), rs.getInt("total")))
			.list();
		List<SuggestedFilter> suggestions = new ArrayList<>();
		reasons.forEach((reason, total) -> {
			if (reason.equals("EMPRESA")) {
				suggestions.addAll(companySuggestions(since));
				return;
			}
			if (total < SUGGESTION_THRESHOLD) {
				return;
			}
			switch (reason) {
				case "SALARIO" -> suggestions.add(new SuggestedFilter(reason, total, "Defina um piso salarial",
						total + " vagas descartadas pelo salário. Inclua a faixa desejada nos alertas do LinkedIn e da Gupy"
								+ " e descarte as que informam valores abaixo dela.",
						null));
				case "STACK" -> {
					String skill = dominant(reason, since, """
							select skill as label, count(*) as total
							from job_feedback f join job_analysis a on a.job_id = f.job_id,
							     jsonb_array_elements_text(a.required_skills) skill
							where f.rating = 'NAO_GOSTEI' and :reason = any(f.reasons) and f.updated_at >= :since
							group by skill order by total desc
							""", resumeSkills);
					suggestions.add(new SuggestedFilter(reason, total,
							(skill != null) ? "Exclua vagas com " + skill : "Revise as palavras-chave dos alertas",
							total + " vagas descartadas pela stack"
									+ ((skill != null) ? "; a skill que mais aparece nelas e não está no seu currículo é "
											+ skill + "." : "."),
							skill));
				}
				case "SENIORIDADE" -> {
					String level = dominant(reason, since, """
							select a.seniority as label, count(*) as total
							from job_feedback f join job_analysis a on a.job_id = f.job_id
							where f.rating = 'NAO_GOSTEI' and :reason = any(f.reasons) and f.updated_at >= :since
							  and a.seniority is not null
							group by a.seniority order by total desc
							""", Set.of());
					String keyword = (level != null) ? SENIORITY_KEYWORDS.get(level) : null;
					suggestions.add(new SuggestedFilter(reason, total,
							(keyword != null) ? "Exclua vagas \"" + keyword + "\"" : "Ajuste a senioridade dos alertas",
							total + " vagas descartadas pela senioridade"
									+ ((keyword != null) ? ", a maioria " + keyword + "." : "."),
							keyword));
				}
				case "MODELO_TRABALHO" -> suggestions.add(new SuggestedFilter(reason, total, "Ative \"só remoto\"",
						total + " vagas descartadas pelo modelo de trabalho. Marque \"só remoto\" nos perfis de busca"
								+ " ou ajuste as localidades aceitas.",
						null));
				case "LOCALIZACAO" -> {
					String place = dominant(reason, since, """
							select j.location as label, count(*) as total
							from job_feedback f join job j on j.id = f.job_id
							where f.rating = 'NAO_GOSTEI' and :reason = any(f.reasons) and f.updated_at >= :since
							  and j.location is not null
							group by j.location order by total desc
							""", Set.of());
					suggestions.add(new SuggestedFilter(reason, total, "Restrinja as localidades",
							total + " vagas descartadas pela localização"
									+ ((place != null) ? ", a mais comum em " + place + "." : "."),
							null));
				}
				default -> {
				}
			}
		});
		return suggestions;
	}

	private List<SuggestedFilter> companySuggestions(Instant since) {
		return this.jdbc.sql("""
				select c.name, count(*) as total
				from job_feedback f join job j on j.id = f.job_id join company c on c.id = j.company_id
				where f.rating = 'NAO_GOSTEI' and 'EMPRESA' = any(f.reasons) and f.updated_at >= :since
				  and c.watch_status <> 'IGNORADA'
				group by c.name having count(*) >= 2 order by total desc limit 5
				""")
			.param("since", Timestamp.from(since))
			.query((rs, row) -> new SuggestedFilter("EMPRESA", rs.getInt("total"), "Ignore " + rs.getString("name"),
					rs.getInt("total") + " vagas de " + rs.getString("name")
							+ " descartadas pela empresa. Marque a empresa como ignorada em Empresas.",
					null))
			.list();
	}

	/** O rótulo mais frequente da consulta, pulando os que já estão no conjunto informado. */
	private @Nullable String dominant(String reason, Instant since, String sql, Set<String> skip) {
		record Row(String label, int total) {
		}
		return this.jdbc.sql(sql)
			.param("reason", reason)
			.param("since", Timestamp.from(since))
			.query((rs, row) -> new Row(rs.getString("label"), rs.getInt("total")))
			.list()
			.stream()
			.filter(row -> !skip.contains(row.label().toLowerCase(Locale.ROOT)))
			.filter(row -> row.total() >= 3)
			.map(Row::label)
			.findFirst()
			.orElse(null);
	}

	/** Fração das classificações da IA mantidas na revisão, nos últimos 90 dias. */
	@Nullable BigDecimal aiAccuracy(Instant since) {
		return this.jdbc.sql("""
				select round(avg(case when ai_classification = final_classification then 1.0 else 0.0 end), 3)
				from email_message
				where ai_classification is not null and final_classification is not null and received_at >= :since
				""").param("since", Timestamp.from(since)).query(BigDecimal.class).optional().orElse(null);
	}

	/**
	 * Custo do mês pela tabela de preços por milhão de tokens: Haiku 5.5 a US$ 0,10 / 0,50 e Opus 5.5 a US$ 4 / 20
	 * (entrada / saída); leitura do cache a 10% e escrita a 125% do preço de entrada.
	 */
	List<LlmUsage> usage(LocalDate monthStart) {
		return this.jdbc.sql("""
				select task, model, count(*) as calls,
				       sum(input_tokens) as input_tokens, sum(output_tokens) as output_tokens,
				       sum(cache_read_tokens) as cache_read_tokens,
				       round(sum(
				           (input_tokens + cache_read_tokens * 0.1 + cache_write_tokens * 1.25) * p.input
				           + output_tokens * p.output) / 1000000, 4) as cost_usd
				from llm_usage u
				cross join lateral (select case when u.model like 'claude-opus%' then 4.0 else 0.10 end as input,
				                           case when u.model like 'claude-opus%' then 20.0 else 0.50 end as output) p
				where u.created_at >= :since
				group by task, model order by cost_usd desc
				""")
			.param("since", Date.valueOf(monthStart))
			.query((rs, row) -> new LlmUsage(rs.getString("task"), rs.getString("model"), rs.getInt("calls"),
					rs.getLong("input_tokens"), rs.getLong("output_tokens"), rs.getLong("cache_read_tokens"),
					rs.getBigDecimal("cost_usd")))
			.list();
	}

	private static int count(List<Reached> rows, Predicate<Reached> stage) {
		return (int) rows.stream().filter(stage).count();
	}

	private record Applied(String source, @Nullable String channel, @Nullable Instant appliedAt,
			@Nullable Instant firstSeenAt, boolean tailored, boolean interview, boolean offer,
			@Nullable Instant firstResponseAt) {
	}

	private record Reached(@Nullable Long resumeVersionId, @Nullable String resumeLabel, @Nullable String seniority,
			@Nullable Integer score, boolean applied, boolean screening, boolean interview, boolean offer) {
	}

}
