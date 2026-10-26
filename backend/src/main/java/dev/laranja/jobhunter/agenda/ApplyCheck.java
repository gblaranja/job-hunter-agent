package dev.laranja.jobhunter.agenda;

import java.util.List;

/**
 * Checagem antes de aplicar: avisos para conferir, nunca um bloqueio.
 *
 * @param linkStatus ATIVO, REMOVIDO, INDETERMINADO (a página não deixou confirmar) ou SEM_LINK
 * @param warnings avisos na ordem de gravidade; vazio quando está tudo certo
 */
public record ApplyCheck(long jobId, String linkStatus, List<Warning> warnings) {

	/**
	 * @param code LINK_FORA_DO_AR, ELIMINATORIO, LEGITIMIDADE, SCORE_BAIXO ou MUITAS_CANDIDATURAS
	 */
	public record Warning(String code, String message) {
	}

}
