package dev.laranja.jobhunter.analysis.internal;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

/** Schemas das saídas estruturadas do LLM usadas pela análise. */
final class LlmOutputs {

	private LlmOutputs() {
	}

	record ScoreAdjustment(@JsonPropertyDescription("Ajuste ao score determinístico, de -10 a 10") int adjustment,
			@JsonPropertyDescription("Uma ou duas frases explicando o ajuste") String justification) {

		int clamped() {
			return Math.max(-10, Math.min(10, this.adjustment));
		}

	}

	record ResumeSkills(@JsonPropertyDescription("Skills técnicas demonstradas no currículo") List<String> skills,
			@JsonPropertyDescription("ESTAGIO, JUNIOR, PLENO, SENIOR ou ESPECIALISTA") String seniority) {
	}

}
