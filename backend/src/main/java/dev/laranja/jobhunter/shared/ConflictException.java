package dev.laranja.jobhunter.shared;

/** O recurso já existe (vaga ou currículo duplicado); vira 409 com o id do existente, quando houver. */
public class ConflictException extends RuntimeException {

	private final Long existingId;

	public ConflictException(String message, Long existingId) {
		super(message);
		this.existingId = existingId;
	}

	public Long existingId() {
		return this.existingId;
	}

}
