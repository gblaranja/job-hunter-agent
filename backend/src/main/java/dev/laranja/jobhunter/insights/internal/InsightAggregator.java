package dev.laranja.jobhunter.insights.internal;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import dev.laranja.jobhunter.insights.InsightReport.Conversion;
import dev.laranja.jobhunter.insights.InsightReport.Funnel;
import dev.laranja.jobhunter.insights.InsightReport.Gap;
import dev.laranja.jobhunter.insights.InsightReport.ResumePerformance;
import dev.laranja.jobhunter.insights.InsightReport.SkillDemand;
import dev.laranja.jobhunter.insights.InsightReport.SuggestedFilter;
import dev.laranja.jobhunter.insights.LlmUsage;
import org.jspecify.annotations.Nullable;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/** Os números do relatório, todos por SQL sobre as vagas, análises, candidaturas, feedbacks e e-mails. */
@Component
class InsightAggregator {

	/** Quantos descartes pelo mesmo motivo bastam para sugerir um ajuste no filtro. */
	static final int SUGGESTION_THRESHOLD = 5;

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

	private record Reached(@Nullable Long resumeVersionId, @Nullable String resumeLabel, @Nullable String seniority,
			@Nullable Integer score, boolean applied, boolean screening, boolean interview, boolean offer) {
	}

}
