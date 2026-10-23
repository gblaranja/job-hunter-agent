package dev.laranja.jobhunter.jobs;

/** De onde a vaga veio. As fontes de coleta entram aqui fase a fase. */
public enum JobOrigin {

	MANUAL, LINKEDIN_ALERTA, GUPY_ALERTA, GREENHOUSE, LEVER, ASHBY, REMOTIVE, GITHUB, ADZUNA;

	/** Fontes que listam todas as vagas abertas da empresa: uma vaga que some da lista foi fechada. */
	public boolean fullListing() {
		return this == GREENHOUSE || this == LEVER || this == ASHBY;
	}

}
