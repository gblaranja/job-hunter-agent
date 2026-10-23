package dev.laranja.jobhunter.tailoring;

import java.time.Instant;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import org.jspecify.annotations.Nullable;

/**
 * Uma versão do currículo adaptada para a vaga.
 *
 * @param status GERANDO, PRONTO ou FALHOU
 * @param content currículo completo em Markdown; nulo enquanto gera ou se falhou
 * @param notCovered requisitos da vaga que o currículo base não comprova (ficaram de fora do texto)
 * @param keywords palavras-chave da vaga (obrigatórias e desejáveis)
 * @param coveredBefore palavras-chave presentes no currículo base
 * @param coveredAfter palavras-chave presentes no texto adaptado
 * @param unsupportedSkills skills que aparecem no texto adaptado e não no currículo base: confira antes de enviar
 * @param scoreBefore score determinístico do currículo base para a vaga
 * @param scoreAfter score determinístico do texto adaptado
 * @param edited o texto foi editado à mão depois de gerado
 * @param resumeVersionId versão do currículo salva a partir deste texto (tela Currículos)
 */
public record TailoredResumeView(long id, long jobId, @Nullable Long baseResumeId, @Nullable String baseResumeLabel,
		String status, @Nullable String content, @Nullable String headline, List<ResumeChange> changes,
		List<String> notCovered, @Nullable String notes, List<String> keywords, List<String> coveredBefore,
		List<String> coveredAfter, List<String> unsupportedSkills, @Nullable Integer scoreBefore,
		@Nullable Integer scoreAfter, boolean edited, @Nullable Long resumeVersionId, @Nullable String resumeLabel,
		@Nullable String modelVersion, @Nullable String lastError, Instant createdAt, Instant updatedAt) {

	/** Uma mudança feita na adaptação. É também parte do schema da saída estruturada do LLM. */
	public record ResumeChange(@JsonPropertyDescription("Seção do currículo (Resumo, Competências, uma experiência...)") String section,
			@JsonPropertyDescription("Trecho do currículo base copiado exatamente; vazio se o trecho é novo") String before,
			@JsonPropertyDescription("Trecho reescrito") String after,
			@JsonPropertyDescription("Uma frase ligando a mudança a um requisito da vaga") String reason) {
	}

}
