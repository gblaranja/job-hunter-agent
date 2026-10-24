package dev.laranja.jobhunter.recognition.internal;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import dev.laranja.jobhunter.companies.CompanyNameNormalizer;
import dev.laranja.jobhunter.shared.ai.AiProperties;
import dev.laranja.jobhunter.shared.ai.LlmClient;
import dev.laranja.jobhunter.shared.ai.LlmClient.Effort;
import dev.laranja.jobhunter.shared.ai.LlmClient.LlmRequest;
import dev.laranja.jobhunter.shared.ai.LlmException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.stereotype.Component;

/**
 * Tira os nomes das empresas de uma lista de premiadas. Com o LLM, entende páginas inteiras; sem ele (ou se falhar),
 * lê uma empresa por linha, tirando a numeração ("1.", "2º -").
 */
@Component
public class RankingListExtractor {

	private static final Logger log = LoggerFactory.getLogger(RankingListExtractor.class);

	private static final Pattern NUMBERED = Pattern.compile("^\\s*(?:[-*•]\\s*)?(?:(\\d{1,4})\\s*(?:[.ºo°)]|-|–)?\\s*[-–:]?\\s*)?(.+?)\\s*$");

	/** O suficiente para uma lista de ~200 empresas com algum ruído da página. */
	static final int MAX_CHARS = 60_000;

	private final LlmClient llm;

	private final AiProperties ai;

	private final String prompt;

	RankingListExtractor(LlmClient llm, AiProperties ai) {
		this.llm = llm;
		this.ai = ai;
		this.prompt = Prompts.load("extract-ranking.v1.md");
	}

	public List<Entry> extract(String text) {
		String input = (text.length() > MAX_CHARS) ? text.substring(0, MAX_CHARS) : text;
		if (this.llm.enabled()) {
			try {
				Extracted extracted = this.llm.structured(new LlmRequest("extract-ranking", this.ai.models().recognition(),
						this.prompt, input, 8000, Effort.LOW), Extracted.class);
				if (extracted.companies() != null) {
					return distinct(extracted.companies()
						.stream()
						.filter(company -> company.name() != null && !company.name().isBlank())
						.map(company -> new Entry(company.name().strip(),
								(company.position() > 0) ? company.position() : null))
						.toList());
				}
			}
			catch (LlmException ex) {
				log.warn("Extração da lista pelo LLM falhou; lendo uma empresa por linha: {}", ex.getMessage());
			}
		}
		return byLines(input);
	}

	static List<Entry> byLines(String text) {
		List<Entry> entries = new ArrayList<>();
		for (String line : text.split("\\R")) {
			Matcher matcher = NUMBERED.matcher(line);
			if (!matcher.matches()) {
				continue;
			}
			String name = matcher.group(2).strip();
			if (name.length() < 2 || name.length() > 80 || CompanyNameNormalizer.normalize(name).isEmpty()) {
				continue;
			}
			Integer position = (matcher.group(1) != null) ? Integer.valueOf(matcher.group(1)) : null;
			entries.add(new Entry(name, position));
		}
		return distinct(entries);
	}

	private static List<Entry> distinct(List<Entry> entries) {
		Map<String, Entry> byName = new LinkedHashMap<>();
		for (Entry entry : entries) {
			byName.putIfAbsent(CompanyNameNormalizer.normalize(entry.name()), entry);
		}
		return List.copyOf(byName.values());
	}

	public record Entry(String name, Integer position) {
	}

	record Extracted(@JsonPropertyDescription("Empresas premiadas, na ordem do texto") List<ExtractedCompany> companies) {
	}

	record ExtractedCompany(@JsonPropertyDescription("Nome da empresa como aparece no texto") String name,
			@JsonPropertyDescription("Posição no ranking; 0 quando não houver") int position) {
	}

}
