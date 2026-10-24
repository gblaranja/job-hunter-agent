package dev.laranja.jobhunter.recognition.internal;

import java.time.LocalDate;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import dev.laranja.jobhunter.recognition.internal.RecognitionSearchClient.SearchHit;
import dev.laranja.jobhunter.shared.ai.AiProperties;
import dev.laranja.jobhunter.shared.ai.LlmClient;
import dev.laranja.jobhunter.shared.ai.LlmClient.Effort;
import dev.laranja.jobhunter.shared.ai.LlmClient.LlmRequest;

import org.springframework.stereotype.Component;

/** Lê os resultados da busca com o Haiku e devolve o reconhecimento que eles comprovam, sem inferir. */
@Component
public class GptwVerifier {

	static final String VERSION = "verify-gptw@v1";

	private final LlmClient llm;

	private final AiProperties ai;

	private final String prompt;

	GptwVerifier(LlmClient llm, AiProperties ai) {
		this.llm = llm;
		this.ai = ai;
		this.prompt = Prompts.load("verify-gptw.v1.md");
	}

	public boolean enabled() {
		return this.llm.enabled();
	}

	public String modelVersion() {
		return this.ai.models().recognition() + "|" + VERSION;
	}

	public Verdict verify(String company, List<SearchHit> hits, LocalDate today) {
		StringBuilder user = new StringBuilder("Empresa pesquisada: ").append(company)
			.append("\nAno atual: ")
			.append(today.getYear())
			.append("\n\nResultados da busca:\n");
		for (int i = 0; i < hits.size(); i++) {
			SearchHit hit = hits.get(i);
			user.append("\n[").append(i + 1).append("] ").append(hit.title()).append("\nURL: ").append(hit.url());
			user.append("\n").append(truncate(hit.content(), 1200)).append("\n");
		}
		return this.llm.structured(new LlmRequest("verify-gptw", this.ai.models().recognition(), this.prompt,
				user.toString(), 1000, Effort.LOW), Verdict.class);
	}

	private static String truncate(String text, int max) {
		return (text.length() <= max) ? text : text.substring(0, max) + "…";
	}

	public record Verdict(
			@JsonPropertyDescription("RANKING_TI, RANKING, CERTIFICACAO ou NAO_ENCONTRADA") String kind,
			@JsonPropertyDescription("Ano do reconhecimento; 0 quando não houver") int year,
			@JsonPropertyDescription("Nome do ranking; vazio para certificação") String rankingName,
			@JsonPropertyDescription("Posição no ranking; 0 quando não houver") int rankingPosition,
			@JsonPropertyDescription("Validade escrita na evidência (AAAA-MM-DD); vazio se não houver") String validUntil,
			@JsonPropertyDescription("URL exata de um dos resultados") String evidenceUrl,
			@JsonPropertyDescription("Trecho que comprova, até 300 caracteres") String evidenceExcerpt,
			@JsonPropertyDescription("Nome da empresa como aparece na evidência") String matchedName,
			@JsonPropertyDescription("Confiança de 0 a 1") double confidence,
			@JsonPropertyDescription("Uma frase explicando a conclusão") String rationale) {
	}

}
