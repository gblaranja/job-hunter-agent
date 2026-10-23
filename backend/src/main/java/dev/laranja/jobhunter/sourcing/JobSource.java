package dev.laranja.jobhunter.sourcing;

import java.util.function.Consumer;

import dev.laranja.jobhunter.jobs.RawJob;

/**
 * Porta de entrada de vagas: cada fonte (alertas do Gmail, ATS das empresas-alvo, APIs públicas) é um
 * adapter. A fonte entrega as vagas uma a uma para a coleta, que filtra, deduplica e grava.
 */
public interface JobSource {

	/** Identificador estável, usado nas estatísticas de cada coleta. */
	String id();

	/** Fontes que dependem de algo não configurado (por exemplo, o Gmail desconectado) ficam de fora. */
	default boolean available() {
		return true;
	}

	void collect(Consumer<RawJob> sink);

}
