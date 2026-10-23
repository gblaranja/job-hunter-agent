package dev.laranja.jobhunter.tracking;

/** Etapas do funil de uma candidatura, na ordem do kanban. */
public enum ApplicationStatus {

	NOVA, INTERESSE, APLICADA, TRIAGEM, ENTREVISTA, TESTE_TECNICO, OFERTA, RECUSADA, DESISTI;

	/** A partir de APLICADA a candidatura precisa registrar qual currículo foi enviado. */
	public boolean requiresResume() {
		return switch (this) {
			case APLICADA, TRIAGEM, ENTREVISTA, TESTE_TECNICO, OFERTA, RECUSADA -> true;
			case NOVA, INTERESSE, DESISTI -> false;
		};
	}

	/** Etapas que encerram a candidatura; sair delas exige uma nota explicando o motivo. */
	public boolean isTerminal() {
		return this == OFERTA || this == RECUSADA || this == DESISTI;
	}

}
