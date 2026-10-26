package dev.laranja.jobhunter.network;

import java.time.Instant;
import java.time.LocalDate;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.jspecify.annotations.Nullable;

/** Comandos aceitos pelo {@link ContactBook}. */
public final class ContactCommands {

	private ContactCommands() {
	}

	/**
	 * Cadastro ou edição de um contato. Só o mínimo (D-05): e-mail só se você quiser anotar.
	 * @param company empresa como você conhece; é casada com o cadastro de empresas pelo nome, sem criar empresa
	 */
	@Schema(name = "SaveContact")
	public record Save(@NotBlank @Size(max = 200) String name, @NotNull ContactRole role,
			@Size(max = 200) @Nullable String company, @Size(max = 300) @Nullable String position,
			@Size(max = 500) @Pattern(regexp = "https?://\\S+", message = "deve ser um link http(s)") @Nullable String linkedinUrl,
			@Size(max = 320) @Email @Nullable String email, @Nullable String notes) {
	}

	/**
	 * Uma conversa com o contato.
	 * @param applicationId candidatura de que a conversa tratou, se houver
	 * @param occurredAt quando foi; sem valor, agora
	 */
	@Schema(name = "RecordInteraction")
	public record RecordInteraction(@NotNull InteractionChannel channel, @NotNull InteractionDirection direction,
			@NotNull InteractionKind kind, @Nullable String summary, @Nullable Long applicationId,
			@Nullable Instant occurredAt) {
	}

	/** Conexão lida do export do LinkedIn. */
	public record Connection(String name, String linkedinUrl, @Nullable String email, @Nullable String company,
			@Nullable String position, @Nullable LocalDate connectedOn) {
	}

}
