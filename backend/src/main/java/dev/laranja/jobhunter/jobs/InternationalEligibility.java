package dev.laranja.jobhunter.jobs;

/**
 * De onde a pessoa pode ser contratada numa vaga do exterior. Os mesmos valores do campo
 * {@code internationalEligibility} do {@code extract-requirements.v2}.
 */
public enum InternationalEligibility {

	/** A vaga cita o Brasil entre os países aceitos. */
	BRASIL,

	/** América Latina, América do Sul ou "Americas". */
	LATAM,

	/** Remoto de qualquer lugar. */
	GLOBAL,

	/** Só para quem mora ou tem autorização de trabalho num país ou região (EUA, Europa, Reino Unido...). */
	PAIS_ESPECIFICO,

	NAO_INFORMADO;

	/** Se uma pessoa no Brasil pode se candidatar; na dúvida (não informado), pode. */
	public boolean eligibleFromBrazil() {
		return this != PAIS_ESPECIFICO;
	}

}
