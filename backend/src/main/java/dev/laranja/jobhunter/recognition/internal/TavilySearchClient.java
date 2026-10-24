package dev.laranja.jobhunter.recognition.internal;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import dev.laranja.jobhunter.recognition.RecognitionProperties;
import org.jspecify.annotations.Nullable;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/** Adapter da API de busca do Tavily ({@code POST /search}), com a chave no header {@code Authorization}. */
@Component
class TavilySearchClient implements RecognitionSearchClient {

	private final RecognitionProperties.Search properties;

	private final RestClient client;

	TavilySearchClient(RecognitionProperties properties, RestClient.Builder http) {
		this.properties = properties.search();
		this.client = http.clone()
			.baseUrl(this.properties.baseUrl())
			.defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + this.properties.apiKey())
			.build();
	}

	@Override
	public String provider() {
		return "tavily";
	}

	@Override
	public boolean enabled() {
		return this.properties.enabled() && "tavily".equalsIgnoreCase(this.properties.provider());
	}

	@Override
	public List<SearchHit> search(SearchQuery query) {
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("query", query.text());
		body.put("search_depth", "basic");
		body.put("max_results", this.properties.maxResults());
		if (!query.includeDomains().isEmpty()) {
			body.put("include_domains", query.includeDomains());
		}
		TavilyResponse response = this.client.post()
			.uri("/search")
			.contentType(MediaType.APPLICATION_JSON)
			.body(body)
			.retrieve()
			.body(TavilyResponse.class);
		if (response == null || response.results() == null) {
			return List.of();
		}
		return response.results()
			.stream()
			.filter(result -> result.url() != null)
			.map(result -> new SearchHit((result.title() != null) ? result.title() : "", result.url(),
					(result.content() != null) ? result.content() : ""))
			.toList();
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record TavilyResponse(@Nullable List<TavilyResult> results) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record TavilyResult(@Nullable String title, @Nullable String url, @Nullable String content) {
	}

}
