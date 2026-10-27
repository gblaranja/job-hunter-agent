package dev.laranja.jobhunter.network;

/** Mensagens que o agente rascunha para você revisar e enviar. */
public enum DraftKind {

	PEDIDO_INDICACAO, FOLLOW_UP, AGRADECIMENTO, RESPOSTA_RECRUTADOR;

	/** Assunto da conversa registrada quando você marca a mensagem como enviada. */
	public InteractionKind interactionKind() {
		return InteractionKind.valueOf(name());
	}

}
