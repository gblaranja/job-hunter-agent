package dev.laranja.jobhunter.analysis.internal;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

/** Eliminatório extraído da vaga, antes da comparação com os dados da pessoa. Também é schema da saída do LLM. */
public record KnockoutRequirement(
		@JsonPropertyDescription("ANOS_EXPERIENCIA, IDIOMA, LOCALIDADE, PRESENCIAL, AUTORIZACAO_TRABALHO, FUSO, FORMACAO, CERTIFICACAO ou CONTRATO") String type,
		@JsonPropertyDescription("Trecho da vaga, curto, copiado como está") String text,
		@JsonPropertyDescription("Valor normalizado conforme o tipo") String value) {
}
