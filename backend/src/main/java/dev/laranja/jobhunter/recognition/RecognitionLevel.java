package dev.laranja.jobhunter.recognition;

/**
 * Nível do selo GPTW. O ranking GPTW TI vale mais que os outros rankings, que valem mais que a certificação; a
 * expirada fica só como histórico.
 */
public enum RecognitionLevel {

	RANKING_TI(10), RANKING(7), CERTIFICADA(4), EXPIRADA(0), NAO_ENCONTRADA(0);

	private final int bonus;

	RecognitionLevel(int bonus) {
		this.bonus = bonus;
	}

	/** Pontos somados ao score da vaga na ordenação da caixa (o {@code match_score} continua sem bônus). */
	public int bonus() {
		return this.bonus;
	}

	/** Os níveis que aparecem como selo. */
	public boolean visible() {
		return this.bonus > 0;
	}

}
