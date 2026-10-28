package dev.laranja.jobhunter.profile;

import java.util.List;

import dev.laranja.jobhunter.jobs.JobFamily;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.jspecify.annotations.Nullable;

/**
 * Cria ou substitui um perfil de busca.
 *
 * @param jobFamilies famílias de cargo de interesse; vazio ou ausente aceita todas
 */
public record SearchProfileCommand(@NotBlank @Size(max = 120) String name, List<String> keywords,
		List<String> excludedKeywords, List<String> locations, boolean remoteOnly, boolean active,
		@Nullable List<JobFamily> jobFamilies) {

	public SearchProfileCommand(String name, List<String> keywords, List<String> excludedKeywords,
			List<String> locations, boolean remoteOnly, boolean active) {
		this(name, keywords, excludedKeywords, locations, remoteOnly, active, List.of());
	}

}
