package dev.laranja.jobhunter.niches.internal;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Clock;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import dev.laranja.jobhunter.niches.NicheSource;
import dev.laranja.jobhunter.niches.internal.NicheDefinitions.NicheDef;
import dev.laranja.jobhunter.niches.internal.NicheDefinitions.SegmentDef;
import dev.laranja.jobhunter.shared.ai.AiProperties;
import dev.laranja.jobhunter.shared.ai.LlmClient;
import dev.laranja.jobhunter.shared.ai.LlmClient.Effort;
import dev.laranja.jobhunter.shared.ai.LlmClient.LlmRequest;
import dev.laranja.jobhunter.shared.ai.LlmException;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * Classifica a empresa nos nichos. O tipo de autorização do Banco Central (ou o CNAE) decide sozinho; sem ele, contam
 * as palavras-chave dos subsegmentos no nome e no setor (peso 3) e nas últimas vagas (peso 1). Quando o resultado é
 * ambíguo, o LLM sugere ({@code source = IA}, não verificado). A sua escolha manual e os cadastros oficiais
 * prevalecem e nunca são sobrescritos.
 */
@Component
public class CompanyNicheClassifier {

	private static final Logger log = LoggerFactory.getLogger(CompanyNicheClassifier.class);

	/** Pontos para classificar sem o LLM: uma palavra-chave no nome, ou três nas vagas. */
	static final int CLEAR_SCORE = 3;

	/** Pontos mínimos para pedir a opinião do LLM (abaixo disso a empresa simplesmente fica fora do nicho). */
	static final int ASK_LLM_SCORE = 2;

	private final NicheDefinitions niches;

	private final JdbcClient jdbc;

	private final LlmClient llm;

	private final AiProperties ai;

	private final Clock clock;

	private final String prompt;

	CompanyNicheClassifier(NicheDefinitions niches, JdbcClient jdbc, LlmClient llm, AiProperties ai, Clock clock) {
		this.niches = niches;
		this.jdbc = jdbc;
		this.llm = llm;
		this.ai = ai;
		this.clock = clock;
		this.prompt = loadPrompt();
	}

	/** Empresas vindas de uma base pública: o subsegmento sai direto do tipo de autorização ou do CNAE. */
	public void assignFromSeed(long companyId, String authorizationType, NicheSource source, List<NicheDef> active) {
		for (NicheDef niche : active) {
			byAuthorization(niche, authorizationType).ifPresent(segment -> {
				if (!manual(companyId, niche.id())) {
					save(companyId, niche.id(), segment.id(), source, 1.0, true);
				}
			});
		}
	}

	/** Classifica todas as empresas com vagas ou vindas das bases públicas (recálculo semanal). */
	public int classifyAll() {
		List<Long> ids = this.jdbc.sql("""
				select c.id from company c
				where c.authorization_type is not null or exists (select 1 from job j where j.company_id = c.id)
				""").query(Long.class).list();
		ids.forEach(this::classify);
		return ids.size();
	}

	public void classify(long companyId) {
		Profile profile = profile(companyId);
		if (profile == null) {
			return;
		}
		Map<Long, Assignment> current = assignments(companyId);
		for (NicheDef niche : this.niches.active()) {
			Assignment existing = current.get(niche.id());
			if (existing != null && (existing.source() == NicheSource.MANUAL || existing.source() == NicheSource.BCB
					|| existing.source() == NicheSource.CNPJ)) {
				continue;
			}
			if (!niche.accepts(profile.kind())) {
				// a empresa mudou de tipo (virou consultoria, por exemplo): sai do nicho que não a aceita
				if (existing != null) {
					delete(companyId, niche.id());
				}
				continue;
			}
			Optional<SegmentDef> official = (profile.authorizationType() != null)
					? byAuthorization(niche, profile.authorizationType()) : Optional.empty();
			if (official.isPresent()) {
				NicheSource source = profile.authorizationType().startsWith("CNAE") ? NicheSource.CNPJ : NicheSource.BCB;
				save(companyId, niche.id(), official.get().id(), source, 1.0, true);
				continue;
			}
			classifyByKeywords(profile, niche, existing);
		}
	}

	private void classifyByKeywords(Profile profile, NicheDef niche, @Nullable Assignment existing) {
		String identity = Keywords.prepare(profile.name() + " " + ((profile.sector() != null) ? profile.sector() : ""));
		String jobs = Keywords.prepare(profile.jobText());
		record Score(SegmentDef segment, int points) {
		}
		List<Score> scores = niche.segments()
			.stream()
			.map(segment -> new Score(segment,
					3 * Keywords.count(identity, segment.keywords()) + Keywords.count(jobs, segment.keywords())))
			.sorted(Comparator.comparingInt(Score::points).reversed())
			.toList();
		Score best = scores.isEmpty() ? null : scores.getFirst();
		int second = (scores.size() > 1) ? scores.get(1).points() : 0;
		if (best == null || best.points() == 0) {
			return;
		}
		if (best.points() >= CLEAR_SCORE && best.points() > second) {
			save(profile.id(), niche.id(), best.segment().id(), NicheSource.VAGA,
					Math.min(0.9, 0.5 + 0.1 * best.points()), false);
			return;
		}
		// ambíguo: uma sugestão anterior da IA (inclusive "fora do nicho") vale até você decidir
		if (existing != null && existing.source() == NicheSource.IA) {
			return;
		}
		if (this.llm.enabled() && best.points() >= ASK_LLM_SCORE) {
			suggest(profile, niche);
		}
		else if (best.points() >= ASK_LLM_SCORE && best.points() > second) {
			save(profile.id(), niche.id(), best.segment().id(), NicheSource.VAGA, 0.4, false);
		}
	}

	private void suggest(Profile profile, NicheDef niche) {
		String segments = niche.segments()
			.stream()
			.map(segment -> "- id " + segment.id() + ": " + segment.name()
					+ ((segment.description() != null) ? " (" + segment.description() + ")" : ""))
			.collect(Collectors.joining("\n"));
		String user = "Nicho: " + niche.name() + "\nSubsegmentos:\n" + segments + "\n\nEmpresa: " + profile.name()
				+ "\nTipo: " + profile.kind() + ((profile.sector() != null) ? "\nSetor: " + profile.sector() : "")
				+ "\nTrechos das vagas:\n" + truncate(profile.jobText(), 4000);
		try {
			Suggestion suggestion = this.llm.structured(new LlmRequest("classify-niche", this.ai.models().classification(),
					this.prompt, user, 600, Effort.LOW), Suggestion.class);
			Long segmentId = niche.segments()
				.stream()
				.map(SegmentDef::id)
				.filter(id -> id == suggestion.segmentId())
				.findFirst()
				.orElse(null);
			save(profile.id(), niche.id(), segmentId, NicheSource.IA, Math.max(0, Math.min(1, suggestion.confidence())),
					false);
		}
		catch (LlmException ex) {
			log.warn("Sugestão de nicho para a empresa {} falhou: {}", profile.id(), ex.getMessage());
		}
	}

	private static Optional<SegmentDef> byAuthorization(NicheDef niche, String authorizationType) {
		return niche.segments()
			.stream()
			.filter(segment -> segment.authorizationTypes().stream().anyMatch(type -> type.equalsIgnoreCase(authorizationType)))
			.findFirst();
	}

	private boolean manual(long companyId, long nicheId) {
		return this.jdbc.sql("select exists (select 1 from company_niche where company_id = :c and niche_id = :n and source = 'MANUAL')")
			.param("c", companyId)
			.param("n", nicheId)
			.query(Boolean.class)
			.single();
	}

	private void save(long companyId, long nicheId, @Nullable Long segmentId, NicheSource source, double confidence,
			boolean verified) {
		this.jdbc.sql("""
				insert into company_niche (company_id, niche_id, segment_id, source, confidence, verified, assigned_at)
				values (:companyId, :nicheId, :segmentId, :source, :confidence, :verified, :now)
				on conflict (company_id, niche_id) do update set segment_id = excluded.segment_id, source = excluded.source,
				    confidence = excluded.confidence, verified = excluded.verified, assigned_at = excluded.assigned_at
				where company_niche.source <> 'MANUAL'
				""")
			.param("companyId", companyId)
			.param("nicheId", nicheId)
			.param("segmentId", segmentId, Types.BIGINT)
			.param("source", source.name())
			.param("confidence", Math.round(confidence * 100) / 100.0)
			.param("verified", verified)
			.param("now", Timestamp.from(this.clock.instant()))
			.update();
	}

	private void delete(long companyId, long nicheId) {
		this.jdbc.sql("delete from company_niche where company_id = :c and niche_id = :n and source <> 'MANUAL'")
			.param("c", companyId)
			.param("n", nicheId)
			.update();
	}

	private @Nullable Profile profile(long companyId) {
		return this.jdbc.sql("""
				select c.id, c.name, c.kind, c.sector, c.authorization_type,
				       coalesce((select string_agg(concat_ws(' ', x.title, x.summary, left(x.raw_description, 1500)), ' ')
				                 from (select j.title, a.summary, j.raw_description from job j
				                       left join job_analysis a on a.job_id = j.id
				                       where j.company_id = c.id order by j.first_seen_at desc limit 20) x), '') as job_text
				from company c where c.id = :id
				""").param("id", companyId).query(Profile.class).optional().orElse(null);
	}

	private Map<Long, Assignment> assignments(long companyId) {
		return this.jdbc.sql("select niche_id, source from company_niche where company_id = :id")
			.param("id", companyId)
			.query((rs, row) -> new Assignment(rs.getLong("niche_id"), NicheSource.valueOf(rs.getString("source"))))
			.list()
			.stream()
			.collect(Collectors.toMap(Assignment::nicheId, assignment -> assignment));
	}

	private static String truncate(String text, int max) {
		return (text.length() <= max) ? text : text.substring(0, max);
	}

	private static String loadPrompt() {
		try {
			return new ClassPathResource("prompts/classify-niche.v1.md").getContentAsString(StandardCharsets.UTF_8).strip();
		}
		catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
	}

	record Profile(long id, String name, String kind, @Nullable String sector, @Nullable String authorizationType,
			String jobText) {
	}

	private record Assignment(long nicheId, NicheSource source) {
	}

	record Suggestion(@JsonPropertyDescription("Id do subsegmento; 0 quando a empresa não pertence ao nicho") long segmentId,
			@JsonPropertyDescription("Confiança de 0 a 1") double confidence,
			@JsonPropertyDescription("Uma frase explicando a escolha") String rationale) {
	}

}
