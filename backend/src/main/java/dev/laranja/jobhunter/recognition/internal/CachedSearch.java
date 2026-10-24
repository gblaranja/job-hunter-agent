package dev.laranja.jobhunter.recognition.internal;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.List;

import dev.laranja.jobhunter.recognition.RecognitionProperties;
import dev.laranja.jobhunter.recognition.internal.RecognitionSearchClient.SearchHit;
import dev.laranja.jobhunter.recognition.internal.RecognitionSearchClient.SearchQuery;
import io.micrometer.core.instrument.MeterRegistry;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * Busca com cache de 6 meses em {@code web_search_cache} e limite mensal de consultas pagas: a mesma consulta dentro
 * do prazo não chega ao provedor.
 */
@Component
public class CachedSearch {

	private static final TypeReference<List<SearchHit>> HITS = new TypeReference<>() {
	};

	private final RecognitionSearchClient client;

	private final RecognitionProperties.Search properties;

	private final JdbcClient jdbc;

	private final JsonMapper json;

	private final MeterRegistry meters;

	private final Clock clock;

	CachedSearch(RecognitionSearchClient client, RecognitionProperties properties, JdbcClient jdbc, JsonMapper json,
			MeterRegistry meters, Clock clock) {
		this.client = client;
		this.properties = properties.search();
		this.jdbc = jdbc;
		this.json = json;
		this.meters = meters;
		this.clock = clock;
	}

	public boolean enabled() {
		return this.client.enabled();
	}

	public String provider() {
		return this.client.provider();
	}

	public List<SearchHit> search(SearchQuery query) {
		String key = hash(query.text() + "|" + String.join(",", query.includeDomains()));
		Instant now = this.clock.instant();
		List<SearchHit> cached = this.jdbc.sql("""
				select response::text from web_search_cache
				where provider = :provider and query_hash = :hash and fetched_at > :since
				""")
			.param("provider", this.client.provider())
			.param("hash", key)
			.param("since", Timestamp.from(now.minus(Duration.ofDays(this.properties.cacheDays()))))
			.query(String.class)
			.optional()
			.map(text -> this.json.readValue(text, HITS))
			.orElse(null);
		if (cached != null) {
			this.meters.counter("jobhunter.recognition.search", "result", "cache").increment();
			return cached;
		}
		if (usedThisMonth() >= this.properties.monthlyLimit()) {
			throw new SearchLimitReachedException(this.properties.monthlyLimit());
		}
		List<SearchHit> hits = this.client.search(query);
		this.meters.counter("jobhunter.recognition.search", "result", "provider").increment();
		this.jdbc.sql("""
				insert into web_search_cache (provider, query_hash, query, response, fetched_at)
				values (:provider, :hash, :query, cast(:response as jsonb), :now)
				on conflict (provider, query_hash) do update set response = excluded.response, fetched_at = excluded.fetched_at
				""")
			.param("provider", this.client.provider())
			.param("hash", key)
			.param("query", query.text())
			.param("response", this.json.writeValueAsString(hits))
			.param("now", Timestamp.from(now))
			.update();
		return hits;
	}

	/** Consultas pagas no mês corrente (cada linha gravada ou renovada no mês foi uma consulta ao provedor). */
	public int usedThisMonth() {
		Instant monthStart = this.clock.instant().atZone(ZoneOffset.UTC).withDayOfMonth(1).truncatedTo(ChronoUnit.DAYS).toInstant();
		return this.jdbc.sql("select count(*) from web_search_cache where provider = :provider and fetched_at >= :since")
			.param("provider", this.client.provider())
			.param("since", Timestamp.from(monthStart))
			.query(Integer.class)
			.single();
	}

	public int monthlyLimit() {
		return this.properties.monthlyLimit();
	}

	private static String hash(String text) {
		try {
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));
		}
		catch (NoSuchAlgorithmException ex) {
			throw new IllegalStateException(ex);
		}
	}

}
