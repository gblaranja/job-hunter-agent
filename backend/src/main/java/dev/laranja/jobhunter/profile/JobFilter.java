package dev.laranja.jobhunter.profile;

import java.util.List;
import java.util.Optional;

import dev.laranja.jobhunter.shared.TextNormalizer;

/**
 * Filtro determinístico aplicado às vagas coletadas, montado a partir dos perfis de busca ativos. As
 * plataformas já filtram pelas palavras-chave dos alertas; aqui ficam as exclusões e o "só remoto".
 *
 * @param excludedKeywords termos que descartam a vaga quando aparecem no título ou na descrição
 * @param remoteOnly descarta vagas que claramente não são remotas
 * @param keywords palavras-chave dos perfis; exigidas só nas fontes amplas
 */
public record JobFilter(List<String> excludedKeywords, boolean remoteOnly, List<String> keywords) {

	public static final JobFilter ACCEPT_ALL = new JobFilter(List.of(), false, List.of());

	public JobFilter(List<String> excludedKeywords, boolean remoteOnly) {
		this(excludedKeywords, remoteOnly, List.of());
	}

	/** Motivo da exclusão, ou vazio se a vaga passa. */
	public Optional<String> rejectionReason(String title, String description, String location, String workModel) {
		String text = " " + TextNormalizer.normalize(title + " " + (description == null ? "" : description)) + " ";
		for (String keyword : this.excludedKeywords) {
			String normalized = TextNormalizer.normalize(keyword);
			if (!normalized.isEmpty() && text.contains(" " + normalized + " ")) {
				return Optional.of("contém \"" + keyword + "\"");
			}
		}
		if (this.remoteOnly && ("PRESENCIAL".equals(workModel) || "HIBRIDO".equals(workModel))) {
			return Optional.of("não é remota");
		}
		return Optional.empty();
	}

	/** Sem palavras-chave configuradas, tudo passa. */
	public boolean matchesKeywords(String title, String description) {
		if (this.keywords.isEmpty()) {
			return true;
		}
		String text = " " + TextNormalizer.normalize(title + " " + (description == null ? "" : description)) + " ";
		return this.keywords.stream()
			.map(TextNormalizer::normalize)
			.anyMatch(keyword -> !keyword.isEmpty() && text.contains(" " + keyword + " "));
	}

}
