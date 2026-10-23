package dev.laranja.jobhunter.profile;

import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Cria ou substitui um perfil de busca. */
public record SearchProfileCommand(@NotBlank @Size(max = 120) String name, List<String> keywords,
		List<String> excludedKeywords, List<String> locations, boolean remoteOnly, boolean active) {
}
