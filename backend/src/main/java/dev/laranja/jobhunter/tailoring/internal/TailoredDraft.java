package dev.laranja.jobhunter.tailoring.internal;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import dev.laranja.jobhunter.tailoring.TailoredResumeView.ResumeChange;

/** Saída estruturada pedida ao modelo na reescrita do currículo para uma vaga. */
public record TailoredDraft(
		@JsonPropertyDescription("Título profissional alinhado à vaga e à senioridade real da pessoa") String headline,
		@JsonPropertyDescription("Currículo completo em Markdown, no formato pedido") String markdown,
		@JsonPropertyDescription("Até 8 mudanças que mais aproximam o currículo da vaga") List<ResumeChange> changes,
		@JsonPropertyDescription("Requisitos da vaga que o currículo base não comprova, com o nome usado na vaga") List<String> notCovered,
		@JsonPropertyDescription("Duas ou três frases: a estratégia da adaptação e o que a pessoa precisa revisar ou preencher") String notes) {
}
