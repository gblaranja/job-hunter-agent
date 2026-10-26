package dev.laranja.jobhunter.tracking;

import java.util.List;

/**
 * Uma empresa onde você foi finalista (chegou à entrevista, ao teste ou à oferta e foi recusado há mais do intervalo
 * configurado) abriu uma vaga parecida. As notificações escutam.
 *
 * @param previousApplicationId a candidatura anterior na empresa
 * @param contacts quem conversou com você na candidatura anterior, para o rascunho de mensagem
 */
public record FinalistJobOpened(long jobId, String title, String company, long previousApplicationId,
		long previousJobId, String previousTitle, List<String> contacts) {
}
