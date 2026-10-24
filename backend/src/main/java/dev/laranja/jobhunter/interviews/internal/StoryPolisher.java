package dev.laranja.jobhunter.interviews.internal;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import dev.laranja.jobhunter.interviews.StarStoryCommand;
import dev.laranja.jobhunter.interviews.StarStoryPolish;
import dev.laranja.jobhunter.shared.ai.AiProperties;
import dev.laranja.jobhunter.shared.ai.LlmClient;
import dev.laranja.jobhunter.shared.ai.LlmClient.Effort;
import dev.laranja.jobhunter.shared.ai.LlmClient.LlmRequest;
import org.jspecify.annotations.Nullable;

import org.springframework.stereotype.Component;

/** Lapida uma história STAR com o modelo das entrevistas, sem fatos novos; números novos voltam como aviso. */
@Component
public class StoryPolisher {

	static final String PROMPT_ID = "polish-story@v1";

	private static final String PROMPT = Prompts.load("prompts/polish-story.v1.md");

	/** Números fora dos marcadores entre colchetes ("[X%]" é para você preencher). */
	private static final Pattern NUMBER = Pattern.compile("\\d+(?:[.,]\\d+)*");

	private static final Pattern PLACEHOLDER = Pattern.compile("\\[[^\\]]*\\]");

	private final LlmClient llm;

	private final AiProperties ai;

	StoryPolisher(LlmClient llm, AiProperties ai) {
		this.llm = llm;
		this.ai = ai;
	}

	public boolean enabled() {
		return this.llm.enabled();
	}

	public StarStoryPolish polish(StarStoryCommand story) {
		String user = """
				Título: %s
				Skills: %s

				Situação:
				%s

				Tarefa:
				%s

				Ação:
				%s

				Resultado:
				%s

				Reflexão:
				%s
				""".formatted(story.title(), (story.skills() != null) ? String.join(", ", story.skills()) : "",
				orEmpty(story.situation()), orEmpty(story.task()), orEmpty(story.action()), orEmpty(story.result()),
				orEmpty(story.reflection()));
		Output output = this.llm.structured(
				new LlmRequest("polish-story", this.ai.models().interviews(), PROMPT, user, 4000, Effort.MEDIUM),
				Output.class);
		StarStoryPolish polished = new StarStoryPolish(orEmpty(output.situation()), orEmpty(output.task()),
				orEmpty(output.action()), orEmpty(output.result()),
				(output.reflection() == null || output.reflection().isBlank()) ? null : output.reflection().strip(),
				orEmpty(output.notes()), List.of());
		List<String> introduced = introducedNumbers(user, String.join("\n", polished.situation(), polished.task(),
				polished.action(), polished.result(), orEmpty(polished.reflection())));
		return introduced.isEmpty() ? polished
				: new StarStoryPolish(polished.situation(), polished.task(), polished.action(), polished.result(),
						polished.reflection(), polished.notes(), List.of("A versão lapidada cita números que não estavam no "
								+ "seu texto: " + String.join(", ", introduced) + ". Confira antes de salvar."));
	}

	/** Números do texto gerado que não aparecem no original (os marcadores entre colchetes não contam). */
	static List<String> introducedNumbers(String original, String generated) {
		Set<String> known = numbers(original);
		List<String> introduced = new ArrayList<>();
		for (String number : numbers(generated)) {
			if (!known.contains(number)) {
				introduced.add(number);
			}
		}
		return introduced;
	}

	private static Set<String> numbers(String text) {
		Set<String> found = new LinkedHashSet<>();
		Matcher matcher = NUMBER.matcher(PLACEHOLDER.matcher(text).replaceAll(" "));
		while (matcher.find()) {
			found.add(matcher.group());
		}
		return found;
	}

	private static String orEmpty(@Nullable String value) {
		return (value == null) ? "" : value.strip();
	}

	record Output(@JsonPropertyDescription("Situação lapidada; vazio se a pessoa não escreveu") @Nullable String situation,
			@JsonPropertyDescription("Tarefa lapidada; vazio se a pessoa não escreveu") @Nullable String task,
			@JsonPropertyDescription("Ação lapidada; vazio se a pessoa não escreveu") @Nullable String action,
			@JsonPropertyDescription("Resultado lapidado; vazio se a pessoa não escreveu") @Nullable String result,
			@JsonPropertyDescription("Reflexão lapidada; vazio se a pessoa não escreveu") @Nullable String reflection,
			@JsonPropertyDescription("O que mudou e o que a pessoa precisa completar") @Nullable String notes) {
	}

}
