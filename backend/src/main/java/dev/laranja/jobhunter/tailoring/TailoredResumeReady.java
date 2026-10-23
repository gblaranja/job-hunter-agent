package dev.laranja.jobhunter.tailoring;

/**
 * O currículo adaptado para a vaga ficou pronto. As notificações escutam este evento.
 *
 * @param coveredKeywords palavras-chave da vaga presentes no texto adaptado
 * @param keywords total de palavras-chave da vaga
 */
public record TailoredResumeReady(long tailoredResumeId, long jobId, int coveredKeywords, int keywords) {
}
