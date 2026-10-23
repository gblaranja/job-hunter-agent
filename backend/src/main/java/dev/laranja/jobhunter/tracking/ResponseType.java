package dev.laranja.jobhunter.tracking;

import java.util.Optional;

/** Tipo de retorno recebido da empresa. */
public enum ResponseType {

	CONVITE_ENTREVISTA, TESTE_TECNICO, PEDIDO_INFORMACAO, OFERTA, RECUSA, OUTRO;

	/** Etapa para onde o retorno leva a candidatura; vazio para os que só ficam registrados. */
	public Optional<ApplicationStatus> targetStatus() {
		return switch (this) {
			case CONVITE_ENTREVISTA -> Optional.of(ApplicationStatus.ENTREVISTA);
			case TESTE_TECNICO -> Optional.of(ApplicationStatus.TESTE_TECNICO);
			case OFERTA -> Optional.of(ApplicationStatus.OFERTA);
			case RECUSA -> Optional.of(ApplicationStatus.RECUSADA);
			case PEDIDO_INFORMACAO, OUTRO -> Optional.empty();
		};
	}

}
