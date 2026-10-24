package dev.laranja.jobhunter.niches.web;

import java.net.URI;
import java.sql.Array;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import dev.laranja.jobhunter.companies.WatchStatus;
import dev.laranja.jobhunter.niches.FitBand;
import dev.laranja.jobhunter.niches.NicheCatalog;
import dev.laranja.jobhunter.niches.NicheCatalog.CreateNiche;
import dev.laranja.jobhunter.niches.NicheCatalog.SegmentCommand;
import dev.laranja.jobhunter.niches.NicheCatalog.UpdateNiche;
import dev.laranja.jobhunter.niches.NicheCatalog.UpdateSegment;
import dev.laranja.jobhunter.niches.NicheSource;
import dev.laranja.jobhunter.shared.NotFoundException;
import dev.laranja.jobhunter.shared.jdbc.JdbcRows;
import dev.laranja.jobhunter.shared.web.PageResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.jspecify.annotations.Nullable;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Tela Nichos: os nichos com subsegmentos e pesos, as empresas ordenadas por fit e a classificação manual. As
 * leituras juntam empresas, vagas, candidaturas e selos direto no SQL.
 */
@RestController
class NicheController {

	private static final TypeReference<List<String>> STRINGS = new TypeReference<>() {
	};

	private static final TypeReference<Map<String, Integer>> COMPONENTS = new TypeReference<>() {
	};

	private static final TypeReference<Map<String, Double>> WEIGHTS = new TypeReference<>() {
	};

	/** Setores dos clientes que contam como "aloca em bancos e fintechs" (comparados sem acento). */
	private static final String FINANCIAL_CLIENTS = "(banc|financ|fintech|pagament|credit|segur|investiment|cart)";

	private final NicheCatalog catalog;

	private final JdbcClient jdbc;

	private final JsonMapper json;

	NicheController(NicheCatalog catalog, JdbcClient jdbc, JsonMapper json) {
		this.catalog = catalog;
		this.jdbc = jdbc;
		this.json = json;
	}

	/** Nichos com os subsegmentos e os números de cada um (para as abas, o mapa e o editor de pesos). */
	@GetMapping("/api/niches")
	List<NicheView> list() {
		record SegmentRow(long nicheId, SegmentView segment) {
		}
		List<SegmentRow> segments = this.jdbc.sql("""
				select s.id, s.niche_id, s.name, s.description, s.base_fit, s.keywords, s.authorization_types,
				       count(cn.company_id) as companies,
				       count(cn.company_id) filter (where f.fit_band = 'ALTO') as high_fit,
				       count(cn.company_id) filter (where exists (select 1 from job j where j.company_id = cn.company_id
				                                                  and j.closed_at is null)) as with_open_jobs,
				       round(avg(f.fit_score)) as avg_fit
				from niche_segment s
				left join company_niche cn on cn.segment_id = s.id
				left join company_fit f on f.company_id = cn.company_id and f.niche_id = s.niche_id
				group by s.id
				order by s.niche_id, s.base_fit desc, s.name
				""")
			.query((rs, row) -> new SegmentRow(rs.getLong("niche_id"), new SegmentView(rs.getLong("id"), rs.getString("name"),
					rs.getString("description"), rs.getInt("base_fit"), strings(rs.getArray("keywords")),
					strings(rs.getArray("authorization_types")), rs.getInt("companies"), rs.getInt("high_fit"),
					rs.getInt("with_open_jobs"), (rs.getObject("avg_fit") != null) ? rs.getInt("avg_fit") : null)))
			.list();
		return this.jdbc.sql("""
				select id, name, description, active, fit_weights::text as weights, company_kinds, domain_terms
				from niche order by id
				""").query((rs, row) -> {
					long id = rs.getLong("id");
					Map<String, Double> weights = this.json.readValue(rs.getString("weights"), WEIGHTS);
					List<SegmentView> own = segments.stream().filter(s -> s.nicheId() == id).map(SegmentRow::segment).toList();
					return new NicheView(id, rs.getString("name"), rs.getString("description"), rs.getBoolean("active"),
							new Weights(weights.getOrDefault("segment", 0.0), weights.getOrDefault("stack", 0.0),
									weights.getOrDefault("domain", 0.0), weights.getOrDefault("signals", 0.0)),
							strings(rs.getArray("company_kinds")), strings(rs.getArray("domain_terms")), own,
							own.stream().mapToInt(SegmentView::companies).sum());
				}).list();
	}

	/**
	 * Empresas do nicho, por fit.
	 * @param segment só um subsegmento
	 * @param gptw só empresas com selo GPTW vigente
	 * @param hasOpenJobs só empresas com vaga aberta agora
	 * @param neverApplied só empresas em que você nunca se candidatou
	 * @param financialClients consultorias que alocam em bancos, fintechs e afins (pelos setores dos clientes)
	 * @param sort fit (padrão), jobs (vagas ativas) ou name
	 */
	@GetMapping("/api/niches/{id}/companies")
	PageResponse<NicheCompany> companies(@PathVariable long id, @RequestParam(required = false) @Nullable Long segment,
			@RequestParam(defaultValue = "false") boolean gptw, @RequestParam(defaultValue = "false") boolean hasOpenJobs,
			@RequestParam(defaultValue = "false") boolean neverApplied,
			@RequestParam(defaultValue = "false") boolean financialClients,
			@RequestParam(required = false) @Nullable String q, @RequestParam(defaultValue = "fit") String sort,
			@RequestParam(defaultValue = "0") @Min(0) int page,
			@RequestParam(defaultValue = "50") @Min(1) @Max(200) int size) {
		requireNiche(id);
		String from = """
				from company_niche cn
				join company c on c.id = cn.company_id
				join niche_segment s on s.id = cn.segment_id
				left join company_fit f on f.company_id = c.id and f.niche_id = cn.niche_id
				left join company_gptw g on g.company_id = c.id
				cross join lateral (
				    select count(*) filter (where j.closed_at is null) as active,
				           count(*) filter (where j.first_seen_at > :since90) as last90
				    from job j where j.company_id = c.id) jobs
				cross join lateral (
				    select count(*) as total from application a join job j on j.id = a.job_id where j.company_id = c.id) apps
				where cn.niche_id = :nicheId
				  and (cast(:segment as bigint) is null or cn.segment_id = :segment)
				  and (not :gptw or g.level is not null)
				  and (not :hasOpenJobs or jobs.active > 0)
				  and (not :neverApplied or apps.total = 0)
				  and (not :financialClients or exists (
				      select 1 from unnest(c.client_sectors) sector
				      where translate(lower(sector), 'áàâãéêíóôõúç', 'aaaaeeiooouc') ~ :financialPattern))
				  and (cast(:pattern as text) is null or c.name ilike :pattern)
				""";
		String order = switch (sort) {
			case "jobs" -> "jobs.active desc, f.fit_score desc nulls last, c.name";
			case "name" -> "c.name";
			default -> "f.fit_score desc nulls last, jobs.active desc, c.name";
		};
		Timestamp since90 = Timestamp.from(Instant.now().minus(90, ChronoUnit.DAYS));
		String pattern = JdbcRows.containsPattern(q);
		List<NicheCompany> items = this.jdbc.sql("""
				select c.id, c.name, c.watch_status, c.kind, c.client_sectors, c.authorization_type, cn.segment_id,
				       s.name as segment_name, cn.source, cn.verified, f.fit_score, f.fit_band, f.reasons::text as reasons,
				       f.components::text as components, g.level as gptw_level, g.year as gptw_year,
				       jobs.active as jobs_active, jobs.last90 as jobs_last90, apps.total as applications,
				       (select array_agg((select count(*) from job j where j.company_id = c.id
				                          and date_trunc('week', j.first_seen_at) = w.week) order by w.week)
				        from generate_series(date_trunc('week', now()) - interval '11 weeks', date_trunc('week', now()),
				                             interval '1 week') w(week)) as trend
				""" + from + " order by " + order + " limit :size offset :offset")
			.param("nicheId", id)
			.param("segment", segment, Types.BIGINT)
			.param("gptw", gptw)
			.param("hasOpenJobs", hasOpenJobs)
			.param("neverApplied", neverApplied)
			.param("financialClients", financialClients)
			.param("financialPattern", FINANCIAL_CLIENTS)
			.param("pattern", pattern, Types.VARCHAR)
			.param("since90", since90)
			.param("size", size)
			.param("offset", page * size)
			.query((rs, row) -> new NicheCompany(rs.getLong("id"), rs.getString("name"),
					WatchStatus.valueOf(rs.getString("watch_status")), rs.getString("kind"), strings(rs.getArray("client_sectors")),
					rs.getString("authorization_type"), rs.getLong("segment_id"), rs.getString("segment_name"),
					NicheSource.valueOf(rs.getString("source")), rs.getBoolean("verified"),
					(rs.getObject("fit_score") != null) ? rs.getInt("fit_score") : null,
					(rs.getString("fit_band") != null) ? FitBand.valueOf(rs.getString("fit_band")) : null,
					(rs.getString("reasons") != null) ? this.json.readValue(rs.getString("reasons"), STRINGS) : List.of(),
					(rs.getString("components") != null) ? this.json.readValue(rs.getString("components"), COMPONENTS)
							: Map.of(),
					rs.getString("gptw_level"), (Integer) rs.getObject("gptw_year"), rs.getInt("jobs_active"),
					rs.getInt("jobs_last90"), rs.getInt("applications"), integers(rs.getArray("trend"))))
			.list();
		long total = this.jdbc.sql("select count(*) " + from)
			.param("nicheId", id)
			.param("segment", segment, Types.BIGINT)
			.param("gptw", gptw)
			.param("hasOpenJobs", hasOpenJobs)
			.param("neverApplied", neverApplied)
			.param("financialClients", financialClients)
			.param("financialPattern", FINANCIAL_CLIENTS)
			.param("pattern", pattern, Types.VARCHAR)
			.param("since90", since90)
			.query(Long.class)
			.single();
		return new PageResponse<>(items, page, size, total);
	}

	@PostMapping("/api/niches")
	ResponseEntity<NicheView> create(@Valid @RequestBody CreateNiche command) {
		long id = this.catalog.create(command);
		return ResponseEntity.created(URI.create("/api/niches/" + id)).body(find(id));
	}

	/** Nome, descrição, ativo, pesos do fit e termos de domínio; o fit do nicho é recalculado. */
	@PatchMapping("/api/niches/{id}")
	NicheView update(@PathVariable long id, @Valid @RequestBody UpdateNiche command) {
		this.catalog.update(id, command);
		return find(id);
	}

	@PostMapping("/api/niches/{id}/segments")
	@ResponseStatus(HttpStatus.CREATED)
	NicheView addSegment(@PathVariable long id, @Valid @RequestBody SegmentCommand command) {
		this.catalog.addSegment(id, command);
		return find(id);
	}

	/** Peso (fit base) e palavras-chave do subsegmento. */
	@PatchMapping("/api/niches/{id}/segments/{segmentId}")
	NicheView updateSegment(@PathVariable long id, @PathVariable long segmentId,
			@Valid @RequestBody UpdateSegment command) {
		this.catalog.updateSegment(id, segmentId, command);
		return find(id);
	}

	/** Reclassifica as empresas e recalcula o fit agora (normalmente roda domingo à noite). */
	@PostMapping("/api/niches/recompute")
	RecomputeResult recompute() {
		return new RecomputeResult(this.catalog.recomputeAll());
	}

	/** Nichos da empresa, com o subsegmento, a origem da classificação e o fit. */
	@GetMapping("/api/companies/{companyId}/niches")
	List<CompanyNiche> companyNiches(@PathVariable long companyId) {
		return this.jdbc.sql("""
				select n.id as niche_id, n.name as niche_name, cn.segment_id, s.name as segment_name, cn.source,
				       cn.verified, cn.confidence, f.fit_score, f.fit_band, f.reasons::text as reasons
				from company_niche cn
				join niche n on n.id = cn.niche_id
				left join niche_segment s on s.id = cn.segment_id
				left join company_fit f on f.company_id = cn.company_id and f.niche_id = cn.niche_id
				where cn.company_id = :companyId
				order by n.id
				""")
			.param("companyId", companyId)
			.query((rs, row) -> new CompanyNiche(rs.getLong("niche_id"), rs.getString("niche_name"),
					(Long) rs.getObject("segment_id"), rs.getString("segment_name"), NicheSource.valueOf(rs.getString("source")),
					rs.getBoolean("verified"), rs.getBigDecimal("confidence").doubleValue(),
					(rs.getObject("fit_score") != null) ? rs.getInt("fit_score") : null,
					(rs.getString("fit_band") != null) ? FitBand.valueOf(rs.getString("fit_band")) : null,
					(rs.getString("reasons") != null) ? this.json.readValue(rs.getString("reasons"), STRINGS) : List.of()))
			.list();
	}

	/** A sua classificação; sem {@code segmentId}, a empresa sai do nicho. Prevalece sobre a automática. */
	@PutMapping("/api/companies/{companyId}/niche")
	List<CompanyNiche> assign(@PathVariable long companyId, @Valid @RequestBody NicheAssignment assignment) {
		this.catalog.assign(companyId, assignment.nicheId(), assignment.segmentId());
		return companyNiches(companyId);
	}

	/** Desfaz a sua classificação no nicho; volta a valer a automática. */
	@DeleteMapping("/api/companies/{companyId}/niches/{nicheId}")
	List<CompanyNiche> clear(@PathVariable long companyId, @PathVariable long nicheId) {
		this.catalog.clearManual(companyId, nicheId);
		return companyNiches(companyId);
	}

	private NicheView find(long id) {
		return list().stream().filter(niche -> niche.id() == id).findFirst().orElseThrow(() -> NotFoundException.of("Nicho", id));
	}

	private void requireNiche(long id) {
		boolean exists = this.jdbc.sql("select exists (select 1 from niche where id = :id)").param("id", id).query(Boolean.class).single();
		if (!exists) {
			throw NotFoundException.of("Nicho", id);
		}
	}

	private static List<String> strings(@Nullable Array array) throws SQLException {
		return (array == null) ? List.of() : Arrays.asList((String[]) array.getArray());
	}

	private static List<Integer> integers(@Nullable Array array) throws SQLException {
		return (array == null) ? List.of()
				: Arrays.stream((Object[]) array.getArray()).map(value -> ((Number) value).intValue()).toList();
	}

	/** Pesos do fit, somando 1. */
	record Weights(double segment, double stack, double domain, double signals) {
	}

	/**
	 * @param companyKinds tipos de empresa aceitos no nicho (vazio: qualquer um)
	 * @param companies empresas classificadas no nicho
	 */
	record NicheView(long id, String name, @Nullable String description, boolean active, Weights fitWeights,
			List<String> companyKinds, List<String> domainTerms, List<SegmentView> segments, int companies) {
	}

	/**
	 * @param baseFit peso do subsegmento no fit (0 a 100)
	 * @param highFit empresas com fit alto
	 * @param avgFit fit médio das empresas do subsegmento
	 */
	record SegmentView(long id, String name, @Nullable String description, int baseFit, List<String> keywords,
			List<String> authorizationTypes, int companies, int highFit, int withOpenJobs, @Nullable Integer avgFit) {
	}

	/**
	 * Empresa na tabela do nicho.
	 * @param components notas de 0 a 100 de cada componente do fit (segment, stack, domain, signals)
	 * @param reasons justificativa do fit
	 * @param gptwLevel selo GPTW vigente
	 * @param trend vagas abertas por semana nas últimas 12 semanas, da mais antiga para a atual
	 */
	record NicheCompany(long id, String name, WatchStatus watchStatus, String kind, List<String> clientSectors,
			@Nullable String authorizationType, long segmentId, String segmentName, NicheSource source, boolean verified,
			@Nullable Integer fitScore, @Nullable FitBand fitBand, List<String> reasons, Map<String, Integer> components,
			@Nullable String gptwLevel, @Nullable Integer gptwYear, int jobsActive, int jobsLast90Days, int applications,
			List<Integer> trend) {
	}

	/** @param segmentId nulo quando a empresa foi tirada do nicho */
	record CompanyNiche(long nicheId, String nicheName, @Nullable Long segmentId, @Nullable String segmentName,
			NicheSource source, boolean verified, double confidence, @Nullable Integer fitScore, @Nullable FitBand fitBand,
			List<String> reasons) {
	}

	/** @param segmentId subsegmento escolhido; nulo tira a empresa do nicho */
	record NicheAssignment(@NotNull Long nicheId, @Nullable Long segmentId) {
	}

	record RecomputeResult(int companies) {
	}

}
