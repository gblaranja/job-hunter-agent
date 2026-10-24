package dev.laranja.jobhunter.niches;

/** Faixa do fit da empresa: alto a partir de 70, médio de 40 a 69, baixo abaixo de 40. */
public enum FitBand {

	ALTO, MEDIO, BAIXO;

	public static FitBand of(int score) {
		return (score >= 70) ? ALTO : (score >= 40) ? MEDIO : BAIXO;
	}

}
