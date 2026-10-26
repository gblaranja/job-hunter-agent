package dev.laranja.jobhunter.sourcing.internal;

import java.util.List;

import dev.laranja.jobhunter.jobs.JobScope;
import dev.laranja.jobhunter.jobs.WorkModel;
import org.jspecify.annotations.Nullable;

/**
 * Prévia de uma vaga importada por link, com os mesmos campos do cadastro manual ({@code POST /api/jobs}).
 *
 * @param scope deixado vazio: o cadastro classifica nacional ou internacional
 * @param method JSON_LD (dados estruturados da página), IA (extraído pelo Claude) ou TEXTO (só o texto, sem IA)
 * @param warnings o que conferir antes de salvar
 * @param existingJobId a vaga já está cadastrada (mesmo link ou mesma empresa e cargo)
 */
public record JobImportPreview(@Nullable String title, @Nullable String company, @Nullable String location,
		@Nullable WorkModel workModel, @Nullable JobScope scope, @Nullable String salaryRange, String url,
		@Nullable String description, String method, List<String> warnings, @Nullable Long existingJobId) {
}
