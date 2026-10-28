package dev.laranja.jobhunter.shared.ai;

/** O orçamento mensal da tarefa acabou: a chamada não foi feita. */
public class LlmBudgetExceededException extends LlmException {

	public LlmBudgetExceededException(String task) {
		super("Orçamento mensal da tarefa " + task + " atingido; a Claude API volta no próximo mês.");
	}

}
