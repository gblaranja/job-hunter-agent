package dev.laranja.jobhunter.network;

import java.time.Instant;
import java.time.LocalDate;

import org.jspecify.annotations.Nullable;

/**
 * Contato como as telas o mostram.
 *
 * @param companyId empresa do cadastro casada pelo nome; nula quando não há empresa com esse nome
 * @param companyName empresa como veio
 * @param lastInteractionAt última conversa registrada
 */
public record ContactView(long id, String name, ContactRole role, @Nullable Long companyId,
		@Nullable String companyName, @Nullable String position, @Nullable String linkedinUrl, @Nullable String email,
		@Nullable String notes, ContactSource source, @Nullable LocalDate connectedOn,
		@Nullable Instant lastInteractionAt, Instant createdAt) {
}
