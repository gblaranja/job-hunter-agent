package dev.laranja.jobhunter.analysis.internal;

import java.util.ArrayList;
import java.util.List;

import org.jspecify.annotations.Nullable;
import tools.jackson.databind.JsonNode;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Busca de skills na API pública da ESCO, a taxonomia europeia de ocupações e skills: devolve a URI estável e os
 * rótulos em português e inglês, para a revisão do dicionário. Só é chamada quando você pede, na tela.
 */
@Component
public class EscoClient {

	private final RestClient http;

	EscoClient(RestClient.Builder http, @Value("${jobhunter.analysis.esco.base-url:https://ec.europa.eu/esco/api}") String baseUrl) {
		this.http = http.baseUrl(baseUrl).build();
	}

	/** Até 5 skills da ESCO para o texto; vazio se a API não responder. */
	public List<EscoSkill> search(String text) {
		JsonNode body;
		try {
			body = this.http.get()
				.uri(uri -> uri.path("/search")
					.queryParam("text", text)
					.queryParam("type", "skill")
					.queryParam("language", "en")
					.queryParam("limit", 5)
					.build())
				.retrieve()
				.body(JsonNode.class);
		}
		catch (RestClientException ex) {
			throw new EscoUnavailableException(ex);
		}
		List<EscoSkill> skills = new ArrayList<>();
		if (body == null) {
			return skills;
		}
		for (JsonNode result : body.path("_embedded").path("results")) {
			String uri = result.path("uri").asString(null);
			if (uri == null) {
				continue;
			}
			JsonNode labels = result.path("preferredLabel");
			skills.add(new EscoSkill(uri, text(labels, "pt"), text(labels, "en", result.path("title").asString(null))));
		}
		return skills;
	}

	private static @Nullable String text(JsonNode labels, String language) {
		return text(labels, language, null);
	}

	private static @Nullable String text(JsonNode labels, String language, @Nullable String fallback) {
		JsonNode value = labels.path(language);
		if (value.isArray() && !value.isEmpty()) {
			value = value.get(0);
		}
		return value.isString() ? value.asString() : fallback;
	}

	/** Skill da ESCO. */
	public record EscoSkill(String uri, @Nullable String labelPt, @Nullable String labelEn) {
	}

	/** A API da ESCO não respondeu. */
	public static class EscoUnavailableException extends RuntimeException {

		EscoUnavailableException(Throwable cause) {
			super("A API da ESCO não respondeu: " + cause.getMessage(), cause);
		}

	}

}
