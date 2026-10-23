package dev.laranja.jobhunter.tracking;

/**
 * Etapas do funil de uma candidatura, na ordem do kanban. {@code ENCERRADA} é a vaga que deixou de aceitar
 * candidaturas, antes ou depois de você aplicar.
 */
public enum ApplicationStatus {

	NOVA, INTERESSE, APLICADA, TRIAGEM, ENTREVISTA, TESTE_TECNICO, OFERTA, RECUSADA, DESISTI, ENCERRADA;

	/** A partir de APLICADA a candidatura precisa registrar qual currículo foi enviado. */
	public boolean requiresResume() {
		return switch (this) {
			case APLICADA, TRIAGEM, ENTREVISTA, TESTE_TECNICO, OFERTA, RECUSADA -> true;
			case NOVA, INTERESSE, DESISTI, ENCERRADA -> false;
		};
	}

	/** Etapas que encerram a candidatura; sair delas exige uma nota explicando o motivo. */
	public boolean isTerminal() {
		return this == OFERTA || this == RECUSADA || this == DESISTI || this == ENCERRADA;
	}

}
