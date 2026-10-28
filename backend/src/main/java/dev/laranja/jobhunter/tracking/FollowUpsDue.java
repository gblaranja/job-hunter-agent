package dev.laranja.jobhunter.tracking;

import java.time.LocalDate;
import java.util.List;

/**
 * Há próximas ações vencidas nas candidaturas (follow-ups, pedidos a responder). Publicado uma vez por dia pelo
 * lembrete das 8h; as notificações escutam.
 *
 * @param day dia do lembrete, para não repetir o aviso
 */
public record FollowUpsDue(LocalDate day, List<DueAction> actions) {

	public record DueAction(long applicationId, String company, NextActionKind kind) {
	}

}
