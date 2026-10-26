package dev.laranja.jobhunter.shared.money;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * PTAX de venda pela API Olinda do Banco Central: {@code CotacaoDolarDia} para o dólar e {@code CotacaoMoedaDia}
 * (boletim de fechamento) para as demais moedas. Usa a cotação do dia ou, antes da divulgação e em fins de semana e
 * feriados, a do último dia útil. Guarda uma cotação por moeda e por dia; uma falha é tentada de novo depois de 15
 * minutos, e até lá o salário aparece "sem câmbio".
 */
@Component
public class PtaxClient implements ExchangeRates {

	static final ZoneId ZONE = ZoneId.of("America/Sao_Paulo");

	private static final DateTimeFormatter OLINDA_DATE = DateTimeFormatter.ofPattern("MM-dd-yyyy");

	private static final Duration RETRY_AFTER_FAILURE = Duration.ofMinutes(15);

	/** Dias para trás procurando a última cotação publicada (feriados emendados com fim de semana). */
	private static final int LOOKBACK_DAYS = 7;

	private static final Logger log = LoggerFactory.getLogger(PtaxClient.class);

	private final RestClient client;

	private final Clock clock;

	private final Map<String, Cached> cache = new ConcurrentHashMap<>();

	PtaxClient(RestClient.Builder http, SalaryProperties properties, Clock clock) {
		SimpleClientHttpRequestFactory requests = new SimpleClientHttpRequestFactory();
		requests.setConnectTimeout(Duration.ofSeconds(5));
		requests.setReadTimeout(Duration.ofSeconds(10));
		this.client = http.clone().baseUrl(properties.ptax().baseUrl()).requestFactory(requests).build();
		this.clock = clock;
	}

	@Override
	public Optional<ExchangeRate> brlPer(String currency) {
		String code = currency.strip().toUpperCase(Locale.ROOT);
		if ("BRL".equals(code)) {
			return Optional.of(new ExchangeRate("BRL", BigDecimal.ONE, LocalDate.now(this.clock.withZone(ZONE))));
		}
		Instant now = this.clock.instant();
		LocalDate today = LocalDate.ofInstant(now, ZONE);
		Cached cached = this.cache.get(code);
		if (cached != null && cached.day().equals(today)
				&& (cached.rate() != null || now.isBefore(cached.fetchedAt().plus(RETRY_AFTER_FAILURE)))) {
			return Optional.ofNullable(cached.rate());
		}
		ExchangeRate rate = fetch(code, today);
		this.cache.put(code, new Cached(today, rate, now));
		return Optional.ofNullable(rate);
	}

	/** Descarta as cotações e falhas guardadas; a próxima consulta vai à API. */
	public void evictCache() {
		this.cache.clear();
	}

	private @Nullable ExchangeRate fetch(String currency, LocalDate today) {
		try {
			for (int back = 0; back <= LOOKBACK_DAYS; back++) {
				LocalDate day = today.minusDays(back);
				BigDecimal value = "USD".equals(currency) ? dollar(day) : currency(currency, day);
				if (value != null) {
					return new ExchangeRate(currency, value, day);
				}
			}
			log.warn("PTAX sem cotação de {} nos últimos {} dias", currency, LOOKBACK_DAYS);
		}
		catch (RestClientException ex) {
			log.warn("PTAX indisponível para {}: {}", currency, ex.getMessage());
		}
		return null;
	}

	private @Nullable BigDecimal dollar(LocalDate day) {
		Quotes quotes = this.client.get()
			.uri(builder -> builder.path("/CotacaoDolarDia(dataCotacao=@dataCotacao)")
				.queryParam("@dataCotacao", "'" + OLINDA_DATE.format(day) + "'")
				.queryParam("$format", "json")
				.build())
			.retrieve()
			.body(Quotes.class);
		return latest(quotes);
	}

	private @Nullable BigDecimal currency(String currency, LocalDate day) {
		Quotes quotes = this.client.get()
			.uri(builder -> builder.path("/CotacaoMoedaDia(moeda=@moeda,dataCotacao=@dataCotacao)")
				.queryParam("@moeda", "'" + currency + "'")
				.queryParam("@dataCotacao", "'" + OLINDA_DATE.format(day) + "'")
				.queryParam("$format", "json")
				.build())
			.retrieve()
			.body(Quotes.class);
		return latest(quotes);
	}

	/** O boletim de fechamento, quando houver; senão o último boletim do dia. */
	private static @Nullable BigDecimal latest(@Nullable Quotes quotes) {
		if (quotes == null || quotes.value() == null || quotes.value().isEmpty()) {
			return null;
		}
		List<Quote> valid = quotes.value().stream().filter(quote -> quote.cotacaoVenda() != null).toList();
		return valid.stream()
			.filter(quote -> "Fechamento".equalsIgnoreCase(quote.tipoBoletim()))
			.findFirst()
			.or(() -> valid.stream()
				.max(Comparator.comparing(quote -> (quote.dataHoraCotacao() != null) ? quote.dataHoraCotacao() : "")))
			.map(Quote::cotacaoVenda)
			.orElse(null);
	}

	private record Cached(LocalDate day, @Nullable ExchangeRate rate, Instant fetchedAt) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record Quotes(@Nullable List<Quote> value) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record Quote(@Nullable BigDecimal cotacaoVenda, @Nullable String dataHoraCotacao, @Nullable String tipoBoletim) {
	}

}
