package dev.laranja.jobhunter.portability.web;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Locale;

import dev.laranja.jobhunter.portability.DataExport;
import dev.laranja.jobhunter.portability.ExportDataset;
import jakarta.validation.constraints.Pattern;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Exportação dos seus dados (B-41). Apagar um contato e as conversas com ele é pelo {@code DELETE /api/contacts/{id}}
 * do módulo de contatos.
 */
@RestController
@RequestMapping("/api/export")
class ExportController {

	private final DataExport export;

	ExportController(DataExport export) {
		this.export = export;
	}

	/**
	 * Baixa um conjunto de dados.
	 * @param dataset VAGAS, CANDIDATURAS, EVENTOS, CONTATOS ou CONVERSAS
	 * @param format json ou csv
	 */
	@GetMapping("/{dataset}")
	ResponseEntity<byte[]> download(@PathVariable ExportDataset dataset,
			@RequestParam(defaultValue = "json") @Pattern(regexp = "json|csv") String format) {
		boolean csv = format.equals("csv");
		String body = csv ? this.export.csv(dataset) : this.export.json(dataset);
		String name = "job-hunter-" + dataset.name().toLowerCase(Locale.ROOT) + "-" + LocalDate.now() + "." + format;
		return ResponseEntity.ok()
			.contentType(csv ? new MediaType("text", "csv", StandardCharsets.UTF_8) : MediaType.APPLICATION_JSON)
			.header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(name).build().toString())
			// BOM para o Excel abrir o CSV em UTF-8
			.body(csv ? ("﻿" + body).getBytes(StandardCharsets.UTF_8) : body.getBytes(StandardCharsets.UTF_8));
	}

}
