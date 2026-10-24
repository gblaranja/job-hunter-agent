package dev.laranja.jobhunter.recognition.internal;

import dev.laranja.jobhunter.shared.BusinessRuleException;

/** O limite mensal de consultas pagas foi atingido; as próximas esperam o mês seguinte. */
public class SearchLimitReachedException extends BusinessRuleException {

	public SearchLimitReachedException(int limit) {
		super("O limite de " + limit + " buscas na web deste mês foi atingido.");
	}

}
