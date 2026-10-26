package dev.laranja.jobhunter.sourcing.web;

import dev.laranja.jobhunter.sourcing.internal.JobImportPreview;
import dev.laranja.jobhunter.sourcing.internal.JobPageImporter;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.jspecify.annotations.Nullable;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Importar vaga por link: a prévia preenche o formulário de cadastro, e o {@code POST /api/jobs} cria a vaga. */
@RestController
@RequestMapping("/api/jobs/import")
class JobImportController {

	private final JobPageImporter importer;

	JobImportController(JobPageImporter importer) {
		this.importer = importer;
	}

	/**
	 * Lê a página da vaga (ou o conteúdo já capturado pelo navegador) e devolve os campos do cadastro. 400 para link
	 * inválido, da rede interna ou do LinkedIn sem conteúdo; 422 quando a página não tem dados de vaga.
	 */
	@PostMapping("/preview")
	JobImportPreview preview(@Valid @RequestBody JobImportRequest request) {
		return this.importer.preview(request.url(), request.jsonLd(), request.text());
	}

	/**
	 * @param url link da vaga (http ou https)
	 * @param jsonLd blocos {@code application/ld+json} da página aberta, como texto de um array JSON; com eles, a
	 * página não é baixada
	 * @param text texto da vaga selecionado no navegador; com ele, a página não é baixada
	 */
	record JobImportRequest(@NotBlank @Size(max = 2000) String url, @Size(max = 1_048_576) @Nullable String jsonLd,
			@Size(max = 50_000) @Nullable String text) {
	}

}
