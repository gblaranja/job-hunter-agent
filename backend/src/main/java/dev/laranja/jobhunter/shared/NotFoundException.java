package dev.laranja.jobhunter.shared;

/** Recurso inexistente; vira 404. */
public class NotFoundException extends RuntimeException {

	public NotFoundException(String message) {
		super(message);
	}

	public static NotFoundException of(String resource, long id) {
		return new NotFoundException(resource + " " + id + " não encontrado(a).");
	}

}
