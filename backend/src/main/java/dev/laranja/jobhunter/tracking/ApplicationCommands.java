package dev.laranja.jobhunter.tracking;

import java.time.Instant;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.jspecify.annotations.Nullable;

/** Comandos aceitos pelo {@link ApplicationService}. */
public final class ApplicationCommands {

	private ApplicationCommands() {
	}

	/**
	 * Marca interesse ou registra uma aplicação.
	 * @param resumeVersionId obrigatório a partir de APLICADA
	 * @param appliedAt data em que você aplicou; útil ao registrar candidaturas antigas
	 */
	@Schema(name = "StartApplication")
	public record Start(@NotNull ApplicationStatus status, @Nullable Long resumeVersionId,
			@Nullable ApplicationChannel channel, @Nullable Instant appliedAt, @Nullable String notes) {
	}

	@Schema(name = "ChangeApplicationStatus")
	public record ChangeStatus(@NotNull ApplicationStatus status, @Nullable Long resumeVersionId,
			@Nullable String note) {
	}

	/**
	 * Retorno recebido fora do e-mail.
	 * @param moveStatus move o card para a etapa do retorno (convite leva a Entrevista, recusa a Recusada…)
	 */
	@Schema(name = "RecordResponse")
	public record RecordResponse(@NotNull ResponseType responseType, @NotNull ResponseChannel channel,
			@Nullable String note, boolean moveStatus) {
	}

	@Schema(name = "ChangeApplicationResume")
	public record ChangeResume(@NotNull Long resumeVersionId) {
	}

	/** Substitui as notas e a próxima ação (nulo limpa o campo). */
	@Schema(name = "UpdateApplicationDetails")
	public record UpdateDetails(@Nullable String notes, @Nullable Instant nextActionAt) {
	}

	/** Cola ou corrige o link e a descrição salvos da vaga aplicada. */
	@Schema(name = "SaveJobSnapshot")
	public record SaveSnapshot(
			@Size(max = 2000) @Pattern(regexp = "https?://\\S+", message = "deve ser um link http(s)") @Nullable String url,
			@NotBlank @Size(max = 300) String title, @NotBlank @Size(max = 200) String company,
			@Nullable String descriptionMd) {
	}

}
