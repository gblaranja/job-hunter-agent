package dev.laranja.jobhunter.analysis;

/**
 * Eliminatório da vaga, já comparado com os dados da pessoa (gravado em {@code job_analysis.knockouts}).
 *
 * @param text trecho da vaga
 * @param value valor normalizado: anos ("8"), idioma e nível ("INGLES:FLUENTE"), local ("Campinas/SP"), dias
 * presenciais por semana ("3"), país ISO ("US"), fuso, nível de formação, certificação ou regime ("PJ")
 * @param reason frase curta explicando o resultado
 */
public record JobKnockout(KnockoutType type, String text, String value, KnockoutStatus status, String reason) {
}
