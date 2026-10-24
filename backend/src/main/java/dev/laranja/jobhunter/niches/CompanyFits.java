package dev.laranja.jobhunter.niches;

import java.sql.Array;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import dev.laranja.jobhunter.companies.CompanyDirectory;
import dev.laranja.jobhunter.niches.internal.FitCalculator;
import dev.laranja.jobhunter.niches.internal.FitCalculator.Inputs;
import dev.laranja.jobhunter.niches.internal.FitCalculator.JobText;
import dev.laranja.jobhunter.niches.internal.FitCalculator.Result;
import dev.laranja.jobhunter.niches.internal.NicheDefinitions;
import dev.laranja.jobhunter.niches.internal.NicheDefinitions.NicheDef;
import dev.laranja.jobhunter.recognition.RecognitionLevel;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.json.JsonMapper;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Fit das empresas em cada nicho, gravado em {@code company_fit} com a justificativa. Uma empresa descoberta sobe
 * para monitorada quando o fit é alto ou quando publicou vaga nos últimos 90 dias.
 */
@Service
@Transactional
public class CompanyFits {

	private final NicheDefinitions niches;

	private final CompanyDirectory directory;

	private final JdbcClient jdbc;

	private final JsonMapper json;

	private final Clock clock;

	CompanyFits(NicheDefinitions niches, CompanyDirectory directory, JdbcClient jdbc, JsonMapper json, Clock clock) {
		this.niches = niches;
		this.directory = directory;
		this.jdbc = jdbc;
		this.json = json;
		this.clock = clock;
	}

	/** Recalcula o fit da empresa em todos os nichos em que ela está. */
	public void recompute(long companyId) {
		this.jdbc.sql("""
				delete from company_fit f where f.company_id = :id
				  and not exists (select 1 from company_niche cn where cn.company_id = f.company_id
				                  and cn.niche_id = f.niche_id and cn.segment_id is not null)
				""").param("id", companyId).update();
		for (NicheDef niche : this.niches.active()) {
			compute(niche, companyId);
		}
	}

	/** Recalcula todas as empresas do nicho (depois de mudar pesos ou subsegmentos). */
	public int recomputeNiche(long nicheId) {
		return this.niches.active().stream().filter(niche -> niche.id() == nicheId).mapToInt(niche -> compute(niche, null)).sum();
	}

	public int recomputeAll() {
		this.jdbc.sql("""
				delete from company_fit f
				where not exists (select 1 from company_niche cn where cn.company_id = f.company_id
				                  and cn.niche_id = f.niche_id and cn.segment_id is not null)
				""").update();
		return this.niches.active().stream().mapToInt(niche -> compute(niche, null)).sum();
	}

	private int compute(NicheDef niche, @Nullable Long companyId) {
		Timestamp since = Timestamp.from(this.clock.instant().minus(Duration.ofDays(90)));
		List<CompanyRow> companies = this.jdbc.sql("""
				select c.id, c.kind, c.client_sectors, s.name as segment, s.base_fit, g.level as gptw_level,
				       g.year as gptw_year,
				       (select count(*) from job j where j.company_id = c.id and j.first_seen_at > :since) as jobs_90
				from company_niche cn
				join company c on c.id = cn.company_id
				join niche_segment s on s.id = cn.segment_id
				left join company_gptw g on g.company_id = c.id
				where cn.niche_id = :nicheId and (cast(:companyId as bigint) is null or c.id = :companyId)
				""")
			.param("since", since)
			.param("nicheId", niche.id())
			.param("companyId", companyId, Types.BIGINT)
			.query((rs, row) -> new CompanyRow(rs.getLong("id"), rs.getString("kind"), strings(rs.getArray("client_sectors")),
					rs.getString("segment"), rs.getInt("base_fit"),
					(rs.getString("gptw_level") != null) ? RecognitionLevel.valueOf(rs.getString("gptw_level")) : null,
					(Integer) rs.getObject("gptw_year"), rs.getInt("jobs_90")))
			.list();
		if (companies.isEmpty()) {
			return 0;
		}
		Map<Long, List<JobText>> jobs = new HashMap<>();
		this.jdbc.sql("""
				select company_id, text, compatible from (
				    select j.company_id,
				           concat_ws(' ', j.title, left(j.raw_description, 4000), a.required_skills::text) as text,
				           (j.work_model in ('REMOTO', 'HIBRIDO') or j.location ilike '%ribeir_o preto%') as compatible,
				           row_number() over (partition by j.company_id order by j.first_seen_at desc, j.id desc) as n
				    from job j
				    join company_niche cn on cn.company_id = j.company_id and cn.niche_id = :nicheId
				    left join job_analysis a on a.job_id = j.id
				    where cast(:companyId as bigint) is null or j.company_id = :companyId) x
				where n <= 10
				""")
			.param("nicheId", niche.id())
			.param("companyId", companyId, Types.BIGINT)
			.query(rs -> {
				jobs.computeIfAbsent(rs.getLong("company_id"), id -> new ArrayList<>())
					.add(JobText.of(rs.getString("text"), rs.getBoolean("compatible")));
			});
		Timestamp now = Timestamp.from(this.clock.instant());
		for (CompanyRow company : companies) {
			Result result = FitCalculator.compute(new Inputs(company.segment(), company.baseFit(),
					"CONSULTORIA_TI".equals(company.kind()), company.clientSectors(), company.gptw(), company.gptwYear(),
					company.jobsLast90Days(), jobs.getOrDefault(company.id(), List.of())), niche.weights(),
					niche.domainTerms());
			this.jdbc.sql("""
					insert into company_fit (company_id, niche_id, fit_score, fit_band, components, reasons, computed_at)
					values (:companyId, :nicheId, :score, :band, cast(:components as jsonb), cast(:reasons as jsonb), :now)
					on conflict (company_id, niche_id) do update set fit_score = excluded.fit_score,
					    fit_band = excluded.fit_band, components = excluded.components, reasons = excluded.reasons,
					    computed_at = excluded.computed_at
					""")
				.param("companyId", company.id())
				.param("nicheId", niche.id())
				.param("score", result.score())
				.param("band", result.band().name())
				.param("components", this.json.writeValueAsString(result.components()))
				.param("reasons", this.json.writeValueAsString(result.reasons()))
				.param("now", now)
				.update();
			if (result.band() == FitBand.ALTO || company.jobsLast90Days() > 0) {
				this.directory.promoteToMonitored(company.id());
			}
		}
		return companies.size();
	}

	private static List<String> strings(@Nullable Array array) throws SQLException {
		return (array == null) ? List.of() : List.of((String[]) array.getArray());
	}

	private record CompanyRow(long id, String kind, List<String> clientSectors, String segment, int baseFit,
			@Nullable RecognitionLevel gptw, @Nullable Integer gptwYear, int jobsLast90Days) {
	}

}
