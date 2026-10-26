package dev.laranja.jobhunter.analysis;

/**
 * Requisito coberto em parte por uma skill equivalente do currículo (vale metade no score).
 *
 * @param skill skill pedida pela vaga
 * @param via skill do currículo que a cobre em parte
 */
public record PartialSkillMatch(String skill, String via) {
}
