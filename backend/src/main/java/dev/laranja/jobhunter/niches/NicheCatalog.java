package dev.laranja.jobhunter.niches;

import java.sql.Timestamp;
import java.sql.Types;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import dev.laranja.jobhunter.companies.CompanyKind;
import dev.laranja.jobhunter.niches.internal.CompanyNicheClassifier;
import dev.laranja.jobhunter.shared.BusinessRuleException;
import dev.laranja.jobhunter.shared.ConflictException;
import dev.laranja.jobhunter.shared.NotFoundException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.json.JsonMapper;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Cadastro dos nichos e subsegmentos (um nicho novo é só cadastro) e a classificação manual das empresas, que
 * prevalece sobre a automática. Mudanças de peso recalculam o fit do nicho.
 */
@Service
@Transactional
public class NicheCatalog {

	private final JdbcClient jdbc;

	private final JsonMapper json;

	private final CompanyFits fits;

	private final CompanyNicheClassifier classifier;

	private final Clock clock;

	NicheCatalog(JdbcClient jdbc, JsonMapper json, CompanyFits fits, CompanyNicheClassifier classifier, Clock clock) {
		this.jdbc = jdbc;
		this.json = json;
		this.fits = fits;
		this.classifier = classifier;
		this.clock = clock;
	}

	public long create(CreateNiche command) {
		long id;
		try {
			id = this.jdbc.sql("""
					insert into niche (name, description, fit_weights, company_kinds, domain_terms)
					values (:name, :description, cast(:weights as jsonb), :kinds, :terms)
					returning id
					""")
				.param("name", command.name().strip())
				.param("description", command.description(), Types.VARCHAR)
				.param("weights", weights((command.fitWeights() != null) ? command.fitWeights() : FitWeights.DEFAULT))
				.param("kinds", command.companyKinds().stream().map(Enum::name).toArray(String[]::new))
				.param("terms", command.domainTerms().toArray(String[]::new))
				.query(Long.class)
				.single();
		}
		catch (DuplicateKeyException ex) {
			throw new ConflictException("Já existe um nicho com esse nome.", null);
		}
		command.segments().forEach(segment -> addSegment(id, segment));
		return id;
	}

	public void update(long id, UpdateNiche command) {
		require(id);
		this.jdbc.sql("""
				update niche set name = coalesce(:name, name), description = coalesce(:description, description),
				    active = coalesce(:active, active), fit_weights = coalesce(cast(:weights as jsonb), fit_weights),
				    domain_terms = coalesce(cast(:terms as text[]), domain_terms)
				where id = :id
				""")
			.param("id", id)
			.param("name", (command.name() != null) ? command.name().strip() : null, Types.VARCHAR)
			.param("description", command.description(), Types.VARCHAR)
			.param("active", command.active(), Types.BOOLEAN)
			.param("weights", (command.fitWeights() != null) ? weights(command.fitWeights()) : null, Types.VARCHAR)
			.param("terms", (command.domainTerms() != null) ? command.domainTerms().toArray(String[]::new) : null,
					Types.ARRAY)
			.update();
		this.fits.recomputeNiche(id);
	}

	public long addSegment(long nicheId, SegmentCommand command) {
		require(nicheId);
		try {
			return this.jdbc.sql("""
					insert into niche_segment (niche_id, name, description, base_fit, keywords, authorization_types)
					values (:nicheId, :name, :description, :baseFit, :keywords, :authorizations)
					returning id
					""")
				.param("nicheId", nicheId)
				.param("name", command.name().strip())
				.param("description", command.description(), Types.VARCHAR)
				.param("baseFit", command.baseFit())
				.param("keywords", command.keywords().toArray(String[]::new))
				.param("authorizations", command.authorizationTypes().toArray(String[]::new))
				.query(Long.class)
				.single();
		}
		catch (DuplicateKeyException ex) {
			throw new ConflictException("O nicho já tem um subsegmento com esse nome.", null);
		}
	}

	public void updateSegment(long nicheId, long segmentId, UpdateSegment command) {
		int updated = this.jdbc.sql("""
				update niche_segment set name = coalesce(:name, name), description = coalesce(:description, description),
				    base_fit = coalesce(:baseFit, base_fit), keywords = coalesce(cast(:keywords as text[]), keywords)
				where id = :segmentId and niche_id = :nicheId
				""")
			.param("nicheId", nicheId)
			.param("segmentId", segmentId)
			.param("name", (command.name() != null) ? command.name().strip() : null, Types.VARCHAR)
			.param("description", command.description(), Types.VARCHAR)
			.param("baseFit", command.baseFit(), Types.INTEGER)
			.param("keywords", (command.keywords() != null) ? command.keywords().toArray(String[]::new) : null, Types.ARRAY)
			.update();
		if (updated == 0) {
			throw NotFoundException.of("Subsegmento", segmentId);
		}
		this.fits.recomputeNiche(nicheId);
	}

	/**
	 * A sua classificação da empresa no nicho; {@code segmentId} nulo tira a empresa do nicho. Prevalece sobre a
	 * classificação automática.
	 */
	public void assign(long companyId, long nicheId, @Nullable Long segmentId) {
		require(nicheId);
		if (segmentId != null) {
			boolean belongs = this.jdbc.sql("select exists (select 1 from niche_segment where id = :id and niche_id = :nicheId)")
				.param("id", segmentId)
				.param("nicheId", nicheId)
				.query(Boolean.class)
				.single();
			if (!belongs) {
				throw new BusinessRuleException("O subsegmento não é deste nicho.");
			}
		}
		boolean exists = this.jdbc.sql("select exists (select 1 from company where id = :id)")
			.param("id", companyId)
			.query(Boolean.class)
			.single();
		if (!exists) {
			throw NotFoundException.of("Empresa", companyId);
		}
		this.jdbc.sql("""
				insert into company_niche (company_id, niche_id, segment_id, source, confidence, verified, assigned_at)
				values (:companyId, :nicheId, :segmentId, 'MANUAL', 1, true, :now)
				on conflict (company_id, niche_id) do update set segment_id = excluded.segment_id, source = 'MANUAL',
				    confidence = 1, verified = true, assigned_at = excluded.assigned_at
				""")
			.param("companyId", companyId)
			.param("nicheId", nicheId)
			.param("segmentId", segmentId, Types.BIGINT)
			.param("now", Timestamp.from(this.clock.instant()))
			.update();
		this.fits.recompute(companyId);
	}

	/** Desfaz a sua escolha: a classificação automática volta a valer. */
	public void clearManual(long companyId, long nicheId) {
		this.jdbc.sql("delete from company_niche where company_id = :companyId and niche_id = :nicheId and source = 'MANUAL'")
			.param("companyId", companyId)
			.param("nicheId", nicheId)
			.update();
		this.classifier.classify(companyId);
		this.fits.recompute(companyId);
	}

	/** Reclassifica todas as empresas e recalcula o fit (normalmente roda no domingo). */
	public int recomputeAll() {
		this.classifier.classifyAll();
		return this.fits.recomputeAll();
	}

	private void require(long nicheId) {
		boolean exists = this.jdbc.sql("select exists (select 1 from niche where id = :id)")
			.param("id", nicheId)
			.query(Boolean.class)
			.single();
		if (!exists) {
			throw NotFoundException.of("Nicho", nicheId);
		}
	}

	/** Grava os pesos normalizados, somando 1. */
	private String weights(FitWeights weights) {
		double total = weights.segment() + weights.stack() + weights.domain() + weights.signals();
		if (total <= 0) {
			throw new BusinessRuleException("Pelo menos um peso precisa ser maior que zero.");
		}
		Map<String, Double> values = new LinkedHashMap<>();
		values.put("segment", round(weights.segment() / total));
		values.put("stack", round(weights.stack() / total));
		values.put("domain", round(weights.domain() / total));
		values.put("signals", round(weights.signals() / total));
		return this.json.writeValueAsString(values);
	}

	private static double round(double value) {
		return Math.round(value * 1000) / 1000.0;
	}

	/** Pesos do fit; são normalizados para somar 1. */
	public record FitWeights(@NotNull @DecimalMin("0") @DecimalMax("1") Double segment,
			@NotNull @DecimalMin("0") @DecimalMax("1") Double stack, @NotNull @DecimalMin("0") @DecimalMax("1") Double domain,
			@NotNull @DecimalMin("0") @DecimalMax("1") Double signals) {

		static final FitWeights DEFAULT = new FitWeights(0.35, 0.30, 0.20, 0.15);

	}

	/**
	 * @param companyKinds tipos de empresa aceitos (vazio aceita qualquer um)
	 * @param domainTerms termos de domínio procurados nas vagas
	 */
	public record CreateNiche(@NotBlank @Size(max = 100) String name, @Nullable String description,
			@Valid @Nullable FitWeights fitWeights, @NotNull List<CompanyKind> companyKinds,
			@NotNull List<@NotBlank String> domainTerms, @NotNull List<@Valid SegmentCommand> segments) {
	}

	public record UpdateNiche(@Size(max = 100) @Nullable String name, @Nullable String description,
			@Nullable Boolean active, @Valid @Nullable FitWeights fitWeights, @Nullable List<@NotBlank String> domainTerms) {
	}

	/** @param authorizationTypes tipos de autorização do Banco Central ou CNAEs que caem direto no subsegmento */
	public record SegmentCommand(@NotBlank @Size(max = 100) String name, @Nullable String description,
			@NotNull @Min(0) @Max(100) Integer baseFit, @NotNull List<@NotBlank String> keywords,
			@NotNull List<@NotBlank String> authorizationTypes) {
	}

	public record UpdateSegment(@Size(max = 100) @Nullable String name, @Nullable String description,
			@Min(0) @Max(100) @Nullable Integer baseFit, @Nullable List<@NotBlank String> keywords) {
	}

}
