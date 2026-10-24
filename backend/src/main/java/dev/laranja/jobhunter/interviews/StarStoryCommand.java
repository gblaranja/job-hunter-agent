package dev.laranja.jobhunter.interviews;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.jspecify.annotations.Nullable;

/**
 * Uma história no formato STAR, escrita por você.
 *
 * @param skills skills que a história comprova; a preparação sugere a história nas perguntas sobre elas
 */
@Schema(name = "StarStoryCommand")
public record StarStoryCommand(@NotBlank @Size(max = 200) String title, @Nullable @Size(max = 5000) String situation,
		@Nullable @Size(max = 5000) String task, @Nullable @Size(max = 5000) String action,
		@Nullable @Size(max = 5000) String result, @Nullable @Size(max = 5000) String reflection,
		@Nullable @Size(max = 20) List<@Size(max = 60) String> skills) {
}
