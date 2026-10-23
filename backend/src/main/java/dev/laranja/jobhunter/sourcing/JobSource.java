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

	/** Fonte ampla (busca em agregadores): só entram vagas com alguma palavra-chave dos perfis de busca. */
	default boolean broad() {
		return false;
	}

	/**
	 * Aplica as exclusões dos perfis. Os ATS das empresas acompanhadas entram completos, para as métricas da
	 * empresa enxergarem todas as vagas; a análise e o score cuidam do resto.
	 */
	default boolean applyExclusions() {
		return true;
	}

	void collect(Consumer<RawJob> sink);

}
