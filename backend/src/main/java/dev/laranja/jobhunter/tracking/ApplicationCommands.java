package dev.laranja.jobhunter.tracking;

import java.time.Instant;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Comandos aceitos pelo {@link ApplicationService}. */
public final class ApplicationCommands {

	private ApplicationCommands() {
	}

	/**
	 * Marca interesse ou registra uma aplicação.
	 * @param resumeVersionId obrigatório a partir de APLICADA
	 * @param appliedAt data em que você aplicou; útil ao registrar candidaturas antigas
	 */
	public record Start(@NotNull ApplicationStatus status, Long resumeVersionId, ApplicationChannel channel,
			Instant appliedAt, String notes) {
	}

	public record ChangeStatus(@NotNull ApplicationStatus status, Long resumeVersionId, String note) {
	}

	public record ChangeResume(@NotNull Long resumeVersionId) {
	}

	/** Substitui as notas e a próxima ação (nulo limpa o campo). */
	public record UpdateDetails(String notes, Instant nextActionAt) {
	}

	/** Cola ou corrige o link e a descrição salvos da vaga aplicada. */
	public record SaveSnapshot(
			@Size(max = 2000) @Pattern(regexp = "https?://\\S+", message = "deve ser um link http(s)") String url,
			@NotBlank @Size(max = 300) String title, @NotBlank @Size(max = 200) String company,
			String descriptionMd) {
	}

}
