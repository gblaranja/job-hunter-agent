package dev.laranja.jobhunter.recognition;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

import dev.laranja.jobhunter.companies.CompanyDiscovered;
import dev.laranja.jobhunter.recognition.internal.CachedSearch;
import dev.laranja.jobhunter.recognition.internal.CompanyMatcher;
import dev.laranja.jobhunter.recognition.internal.GptwVerifier;
import dev.laranja.jobhunter.recognition.internal.GptwVerifier.Verdict;
import dev.laranja.jobhunter.recognition.internal.RecognitionRules;
import dev.laranja.jobhunter.recognition.internal.RecognitionRules.Decision;
import dev.laranja.jobhunter.recognition.internal.RecognitionSearchClient.SearchHit;
import dev.laranja.jobhunter.recognition.internal.RecognitionSearchClient.SearchQuery;
import dev.laranja.jobhunter.recognition.internal.SearchLimitReachedException;
import dev.laranja.jobhunter.shared.BusinessRuleException;
import dev.laranja.jobhunter.shared.NotFoundException;
import dev.laranja.jobhunter.shared.ai.LlmException;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.RestClientException;

/**
 * Selo GPTW: busca na web com as variações do nome (a página oficial do GPTW primeiro), verificação pelo Haiku sob a
 * regra "sem menção explícita à empresa e ao ano, NAO_ENCONTRADA" e o nível calculado pelas regras. Só verifica
 * empresas com vaga ativa ou monitoradas/alvo: as importações do Banco Central e da Receita não disparam buscas.
 */
@Service
@EnableConfigurationProperties(RecognitionProperties.class)
public class GptwRecognitionService {

	private static final Logger log = LoggerFactory.getLogger(GptwRecognitionService.class);

	private static final ZoneId ZONE = ZoneId.of("America/Sao_Paulo");

	static final List<String> OFFICIAL_DOMAINS = List.of("gptw.com.br", "greatplacetowork.com.br");

	private final CachedSearch search;

	private final GptwVerifier verifier;

	private final CompanyMatcher matcher;

	private final RecognitionProperties properties;

	private final JdbcClient jdbc;

	private final ApplicationEventPublisher events;

	private final Clock clock;

	private final TransactionTemplate transactions;

	/** Uma verificação por vez: a coleta pode descobrir dezenas de empresas de uma vez. */
	private final ReentrantLock lock = new ReentrantLock();

	GptwRecognitionService(CachedSearch search, GptwVerifier verifier, CompanyMatcher matcher,
			RecognitionProperties properties, JdbcClient jdbc, ApplicationEventPublisher events, Clock clock,
			PlatformTransactionManager transactionManager) {
		this.search = search;
		this.verifier = verifier;
		this.matcher = matcher;
		this.properties = properties;
		this.jdbc = jdbc;
		this.events = events;
		this.clock = clock;
		this.transactions = new TransactionTemplate(transactionManager);
	}

	/** A busca precisa da API de busca e do LLM configurados. */
	public boolean enabled() {
		return this.search.enabled() && this.verifier.enabled();
	}

	@ApplicationModuleListener
	void on(CompanyDiscovered event) {
		if (!enabled() || !eligible(event.companyId()) || checkedRecently(event.companyId())) {
			return;
		}
		tryCheck(event.companyId());
	}

	/** Reverifica as empresas elegíveis com a verificação vencida, dentro do limite mensal de buscas. */
	@Scheduled(cron = "${jobhunter.recognition.recheck-cron:0 0 4 2 * *}", zone = "America/Sao_Paulo")
	@SchedulerLock(name = "gptw-recheck", lockAtMostFor = "PT2H")
	void monthly() {
		recheckExpired();
	}

	public int recheckExpired() {
		if (!enabled()) {
			return 0;
		}
		List<Long> due = this.jdbc.sql("""
				select c.id from company c
				where (c.watch_status in ('MONITORADA', 'ALVO')
				       or exists (select 1 from job j where j.company_id = c.id and j.closed_at is null))
				  and c.watch_status <> 'IGNORADA'
				  and not exists (select 1 from company_recognition r
				                  where r.company_id = c.id and r.source = 'BUSCA' and r.checked_at > :since)
				order by (select count(*) from job j where j.company_id = c.id and j.closed_at is null) desc, c.id
				""")
			.param("since", Timestamp.from(this.clock.instant().minus(Duration.ofDays(this.properties.recheckAfterDays()))))
			.query(Long.class)
			.list();
		int checked = 0;
		for (long companyId : due) {
			if (this.search.usedThisMonth() + 2 > this.search.monthlyLimit()) {
				log.info("Reverificação GPTW parou no limite mensal de buscas; {} empresas ficam para o próximo mês.",
						due.size() - checked);
				break;
			}
			if (tryCheck(companyId)) {
				checked++;
			}
		}
		return checked;
	}

	/** Verificação sob demanda (botão no detalhe da empresa). As respostas em cache não gastam consultas. */
	public RecognitionView check(long companyId) {
		if (!enabled()) {
			throw new BusinessRuleException(
					"Configure a API de busca (JOBHUNTER_SEARCH_API_KEY) e a Claude API para verificar o selo GPTW.");
		}
		long id = verify(companyId);
		return RecognitionQueries.get(this.jdbc, id, today());
	}

	private boolean tryCheck(long companyId) {
		try {
			verify(companyId);
			return true;
		}
		catch (SearchLimitReachedException ex) {
			log.info("Verificação GPTW da empresa {} adiada: {}", companyId, ex.getMessage());
		}
		catch (LlmException | RestClientException ex) {
			log.warn("Verificação GPTW da empresa {} falhou; o job mensal tenta de novo: {}", companyId, ex.getMessage());
		}
		return false;
	}

	private long verify(long companyId) {
		this.lock.lock();
		try {
			return verifyLocked(companyId);
		}
		finally {
			this.lock.unlock();
		}
	}

	private long verifyLocked(long companyId) {
		record Company(long id, String name, String normalizedName) {
		}
		Company company = this.jdbc.sql("select id, name, normalized_name from company where id = :id")
			.param("id", companyId)
			.query(Company.class)
			.optional()
			.orElseThrow(() -> NotFoundException.of("Empresa", companyId));
		LocalDate today = today();
		String name = searchName(company.name());
		List<SearchHit> hits = new ArrayList<>();
		hits.addAll(this.search.search(new SearchQuery("\"" + name + "\" GPTW certificação", OFFICIAL_DOMAINS)));
		hits.addAll(this.search.search(new SearchQuery(
				"\"" + name + "\" \"Great Place to Work\" ranking " + today.getYear(), List.of())));
		List<SearchHit> results = officialFirst(hits);
		Decision decision;
		if (results.isEmpty()) {
			decision = RecognitionRules.decide(new Verdict("NAO_ENCONTRADA", 0, "", 0, "", "", "", "", 1,
					"A busca não trouxe resultados."), List.of(), company.normalizedName(), List.of(), List.of(), today,
					this.properties.reviewThreshold());
		}
		else {
			Verdict verdict = this.verifier.verify(company.name(), results, today);
			decision = RecognitionRules.decide(verdict, results.stream().map(SearchHit::url).toList(),
					company.normalizedName(), this.matcher.aliases(companyId),
					this.matcher.homonyms(companyId, company.normalizedName()), today, this.properties.reviewThreshold());
		}
		Decision result = decision;
		// Os eventos só saem com a transação: os listeners do Modulith rodam depois do commit.
		Long id = this.transactions.execute(status -> {
			long saved = save(companyId, result);
			if (result.review() == RecognitionRules.Review.PENDENTE) {
				this.events.publishEvent(new RecognitionReviewNeeded(saved, companyId, company.name(), result.level(),
						result.year()));
			}
			else if (result.level().visible()) {
				this.events.publishEvent(new RecognitionChanged(companyId));
			}
			return saved;
		});
		return id;
	}

	/**
	 * Grava o resultado. Um reconhecimento igual ao último (mesmo nível, ano e ranking) só renova a data da
	 * verificação, para o histórico não repetir linhas a cada reverificação.
	 */
	private long save(long companyId, Decision decision) {
		Long same = this.jdbc.sql("""
				select id from company_recognition
				where company_id = :companyId and program = 'GPTW' and level = :level
				  and year is not distinct from :year and ranking_name is not distinct from :rankingName
				  and review_status <> 'REJEITADO'
				order by checked_at desc limit 1
				""")
			.param("companyId", companyId)
			.param("level", decision.level().name())
			.param("year", decision.year(), java.sql.Types.INTEGER)
			.param("rankingName", decision.rankingName(), java.sql.Types.VARCHAR)
			.query(Long.class)
			.optional()
			.orElse(null);
		Timestamp now = Timestamp.from(this.clock.instant());
		if (same != null) {
			this.jdbc.sql("update company_recognition set checked_at = :now where id = :id")
				.param("now", now)
				.param("id", same)
				.update();
			return same;
		}
		return this.jdbc.sql("""
				insert into company_recognition (company_id, program, level, ranking_name, ranking_position, year,
				    valid_until, evidence_url, evidence_excerpt, rationale, matched_name, confidence, verified_by,
				    review_status, source, checked_at)
				values (:companyId, 'GPTW', :level, :rankingName, :position, :year, :validUntil, :evidenceUrl, :excerpt,
				        :rationale, :matchedName, :confidence, 'IA', :review, 'BUSCA', :now)
				returning id
				""")
			.param("companyId", companyId)
			.param("level", decision.level().name())
			.param("rankingName", decision.rankingName(), java.sql.Types.VARCHAR)
			.param("position", decision.rankingPosition(), java.sql.Types.INTEGER)
			.param("year", decision.year(), java.sql.Types.INTEGER)
			.param("validUntil", (decision.validUntil() != null) ? Date.valueOf(decision.validUntil()) : null,
					java.sql.Types.DATE)
			.param("evidenceUrl", truncate(decision.evidenceUrl(), 1000), java.sql.Types.VARCHAR)
			.param("excerpt", decision.evidenceExcerpt(), java.sql.Types.VARCHAR)
			.param("rationale", decision.rationale(), java.sql.Types.VARCHAR)
			.param("matchedName", truncate(decision.matchedName(), 200), java.sql.Types.VARCHAR)
			.param("confidence", Math.round(decision.confidence() * 100) / 100.0)
			.param("review", decision.review().name())
			.param("now", now)
			.query(Long.class)
			.single();
	}

	private boolean eligible(long companyId) {
		return this.jdbc.sql("""
				select exists (select 1 from company c where c.id = :id and c.watch_status <> 'IGNORADA'
				    and (c.watch_status in ('MONITORADA', 'ALVO')
				         or exists (select 1 from job j where j.company_id = c.id and j.closed_at is null)))
				""").param("id", companyId).query(Boolean.class).single();
	}

	private boolean checkedRecently(long companyId) {
		return this.jdbc.sql("""
				select exists (select 1 from company_recognition
				               where company_id = :id and source = 'BUSCA' and checked_at > :since)
				""")
			.param("id", companyId)
			.param("since", Timestamp.from(this.clock.instant().minus(Duration.ofDays(this.properties.recheckAfterDays()))))
			.query(Boolean.class)
			.single();
	}

	/** O nome sem a forma societária, entre aspas na busca ("Itaú Unibanco S.A." vira "Itaú Unibanco"). */
	static String searchName(String name) {
		return name.replaceAll("(?i)[\\s,]+(ltda\\.?|s\\.?\\s?/?a\\.?|eireli|me|epp|inc\\.?|llc)\\s*$", "").strip();
	}

	/** A página oficial do GPTW vem antes de notícias e posts; URLs repetidas entram uma vez. */
	static List<SearchHit> officialFirst(List<SearchHit> hits) {
		Map<String, SearchHit> byUrl = new LinkedHashMap<>();
		hits.stream().filter(hit -> official(hit.url())).forEach(hit -> byUrl.putIfAbsent(hit.url(), hit));
		hits.stream().filter(hit -> !official(hit.url())).forEach(hit -> byUrl.putIfAbsent(hit.url(), hit));
		return byUrl.values().stream().limit(8).toList();
	}

	private static boolean official(String url) {
		String lower = url.toLowerCase(Locale.ROOT);
		return OFFICIAL_DOMAINS.stream().anyMatch(domain -> lower.contains("://" + domain) || lower.contains("." + domain));
	}

	private LocalDate today() {
		return LocalDate.now(this.clock.withZone(ZONE));
	}

	private static String truncate(String value, int max) {
		return (value == null || value.length() <= max) ? value : value.substring(0, max);
	}

}
