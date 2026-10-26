package dev.laranja.jobhunter.inbox;

/**
 * O que um e-mail da label de vagas é. Os tipos de retorno movem a candidatura correspondente.
 * {@code CONFIRMACAO_CANDIDATURA} ("recebemos sua candidatura") registra ou confirma a aplicação, e
 * {@code CONTATO_RECRUTADOR} é um recrutador ou agência oferecendo uma oportunidade; nenhum dos dois é retorno.
 */
public enum EmailClassification {

	ALERTA_VAGA, CONVITE_ENTREVISTA, TESTE_TECNICO, PEDIDO_INFORMACAO, OFERTA, RECUSA, CONFIRMACAO_CANDIDATURA,
	CONTATO_RECRUTADOR, OUTRO, IRRELEVANTE;

	/** Retornos de uma empresa sobre uma candidatura (o que entra no funil). */
	public boolean isResponse() {
		return switch (this) {
			case CONVITE_ENTREVISTA, TESTE_TECNICO, PEDIDO_INFORMACAO, OFERTA, RECUSA -> true;
			case ALERTA_VAGA, CONFIRMACAO_CANDIDATURA, CONTATO_RECRUTADOR, OUTRO, IRRELEVANTE -> false;
		};
	}

	/** E-mails que outros módulos tratam: os retornos, as confirmações de candidatura e as abordagens. */
	public boolean isActionable() {
		return isResponse() || this == CONFIRMACAO_CANDIDATURA || this == CONTATO_RECRUTADOR;
	}

}
