package dev.laranja.jobhunter.jobs;

/**
 * Chance de a vaga ser real e estar aberta. A caixa de vagas esconde BAIXA e SUSPEITA por padrão, e MEDIA perde
 * posições na ordenação.
 */
public enum Legitimacy {

	/** Nenhum sinal de vaga fantasma. */
	ALTA,
	/** Um ou dois sinais fracos (vaga antiga, republicada, fora do ATS da empresa). */
	MEDIA,
	/** Vários sinais de vaga fantasma ou já fechada. */
	BAIXA,
	/** Sinal de golpe: pede pagamento ou junta contato só por WhatsApp com e-mail gratuito. */
	SUSPEITA

}
