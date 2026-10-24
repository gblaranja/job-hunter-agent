package dev.laranja.jobhunter.jobs;

/** De onde a vaga veio. As fontes de coleta entram aqui fase a fase. */
public enum JobOrigin {

	MANUAL, LINKEDIN_ALERTA, GUPY_ALERTA, GREENHOUSE, LEVER, ASHBY, REMOTIVE, GITHUB, ADZUNA, SMARTRECRUITERS, WORKABLE,
	TEAMTAILOR, RECRUITEE;

	/** Fontes que listam todas as vagas abertas da empresa: uma vaga que some da lista foi fechada. */
	public boolean fullListing() {
		return switch (this) {
			case GREENHOUSE, LEVER, ASHBY, SMARTRECRUITERS, WORKABLE, TEAMTAILOR, RECRUITEE -> true;
			default -> false;
		};
	}

}
