package dev.laranja.jobhunter.jobs;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.jspecify.annotations.Nullable;

/**
 * Vaga cadastrada à mão: você cola o link e a descrição no formulário.
 *
 * @param scope nacional ou internacional; sem valor, sai da localidade e do idioma da descrição ou do título
 */
public record ManualJobCommand(@NotBlank @Size(max = 300) String title, @NotBlank @Size(max = 200) String company,
		@Size(max = 200) @Nullable String location, @Nullable WorkModel workModel, @Nullable JobScope scope,
		@Size(max = 100) @Nullable String salaryRange,
		@Size(max = 2000) @Pattern(regexp = "https?://\\S+", message = "deve ser um link http(s)") @Nullable String url,
		@Nullable String description) {
}
