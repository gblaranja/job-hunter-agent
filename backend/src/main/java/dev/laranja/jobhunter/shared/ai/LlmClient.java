package dev.laranja.jobhunter.shared.ai;

/** Porta para o LLM: uma chamada com saída estruturada, validada contra o tipo pedido. */
public interface LlmClient {

	boolean enabled();

	<T> T structured(LlmRequest request, Class<T> outputType);

	/**
	 * @param task nome da tarefa, para o registro de uso
	 * @param system instruções fixas (e o currículo, quando houver); ficam em cache entre as chamadas
	 * @param user conteúdo variável da chamada
	 * @param effort LOW para extração e classificação; HIGH para o relatório semanal
	 * @param thinking pensamento adaptativo, para as tarefas que pedem raciocínio (o relatório semanal)
	 */
	record LlmRequest(String task, String model, String system, String user, long maxTokens, Effort effort,
			boolean thinking) {

		public LlmRequest(String task, String model, String system, String user, long maxTokens, Effort effort) {
			this(task, model, system, user, maxTokens, effort, false);
		}

	}

	enum Effort {

		LOW, MEDIUM, HIGH

	}

}
