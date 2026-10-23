package dev.laranja.jobhunter.inbox;

/** O que um e-mail da label de vagas é. Os tipos de retorno movem a candidatura correspondente. */
public enum EmailClassification {

	ALERTA_VAGA, CONVITE_ENTREVISTA, TESTE_TECNICO, PEDIDO_INFORMACAO, OFERTA, RECUSA, OUTRO, IRRELEVANTE;

	/** Retornos de uma empresa sobre uma candidatura (o que entra no funil). */
	public boolean isResponse() {
		return switch (this) {
			case CONVITE_ENTREVISTA, TESTE_TECNICO, PEDIDO_INFORMACAO, OFERTA, RECUSA -> true;
			case ALERTA_VAGA, OUTRO, IRRELEVANTE -> false;
		};
	}

}
