package dev.laranja.jobhunter.network;

import org.jspecify.annotations.Nullable;

/**
 * Rascunho pronto para copiar. Nada é guardado nem enviado.
 *
 * @param linkedinNote convite de conexão do LinkedIn, com até 300 caracteres (só no pedido de indicação)
 * @param language idioma da vaga: pt ou en
 * @param source IA (Claude API) ou MODELO (texto fixo, sem a API)
 */
public record MessageDraft(DraftKind kind, String text, @Nullable String linkedinNote, String language,
		String source) {

	/** Limite do convite de conexão do LinkedIn. */
	public static final int LINKEDIN_NOTE_LIMIT = 300;

}
