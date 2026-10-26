package dev.laranja.jobhunter.analysis;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

/**
 * Trecho do currículo que comprova um requisito da vaga. Só é gravado quando o trecho está no texto do currículo.
 * Também é schema da saída do LLM.
 */
public record RequirementEvidence(
		@JsonPropertyDescription("Requisito da vaga, como veio na lista") String requirement,
		@JsonPropertyDescription("Trecho copiado literalmente do currículo, até 200 caracteres") String quote) {
}
