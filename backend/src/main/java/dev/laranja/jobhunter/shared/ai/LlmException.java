package dev.laranja.jobhunter.shared.ai;

/** A chamada ao LLM não produziu um resultado utilizável (erro da API, recusa, resposta truncada ou inválida). */
public class LlmException extends RuntimeException {

	public LlmException(String message) {
		super(message);
	}

	public LlmException(String message, Throwable cause) {
		super(message, cause);
	}

}
