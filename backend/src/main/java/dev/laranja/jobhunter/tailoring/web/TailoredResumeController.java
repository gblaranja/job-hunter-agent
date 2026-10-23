package dev.laranja.jobhunter.tailoring.web;

import java.net.URI;
import java.util.List;

import dev.laranja.jobhunter.resumes.ResumeRef;
import dev.laranja.jobhunter.tailoring.TailoredResumeView;
import dev.laranja.jobhunter.tailoring.TailoredResumes;
import dev.laranja.jobhunter.tailoring.TailoredResumes.Availability;
import dev.laranja.jobhunter.tailoring.TailoredResumes.Document;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.jspecify.annotations.Nullable;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
class TailoredResumeController {

	private static final MediaType DOCX = MediaType
		.parseMediaType("application/vnd.openxmlformats-officedocument.wordprocessingml.document");

	private final TailoredResumes tailored;

	TailoredResumeController(TailoredResumes tailored) {
		this.tailored = tailored;
	}

	/** Se a adaptação está disponível nesta instalação (precisa da chave da Claude API). */
	@GetMapping("/api/tailored-resumes/availability")
	Availability tailoringAvailability() {
		return this.tailored.availability();
	}

	/** Versões adaptadas para a vaga, da mais nova para a mais antiga. */
	@GetMapping("/api/jobs/{jobId}/tailored-resumes")
	List<TailoredResumeView> tailoredResumes(@PathVariable long jobId) {
		return this.tailored.forJob(jobId);
	}

	/** Pede uma versão nova; a reescrita roda em segundo plano (status GERANDO até ficar pronta). */
	@PostMapping("/api/jobs/{jobId}/tailored-resumes")
	ResponseEntity<TailoredResumeView> requestTailoredResume(@PathVariable long jobId,
			@RequestBody(required = false) @Nullable TailorRequest body) {
		TailoredResumeView created = this.tailored.request(jobId, (body != null) ? body.baseResumeId() : null);
		return ResponseEntity.accepted().location(URI.create("/api/tailored-resumes/" + created.id())).body(created);
	}

	@GetMapping("/api/tailored-resumes/{id}")
	TailoredResumeView tailoredResume(@PathVariable long id) {
		return this.tailored.get(id);
	}

	/** Troca o texto por uma edição manual e recalcula a cobertura das palavras-chave. */
	@PutMapping("/api/tailored-resumes/{id}/content")
	TailoredResumeView editTailoredResume(@PathVariable long id, @Valid @RequestBody TailoredContent body) {
		return this.tailored.edit(id, body.content());
	}

	/** Gera o DOCX e o salva como uma versão do currículo, para escolher na candidatura. */
	@PostMapping("/api/tailored-resumes/{id}/resume-version")
	ResponseEntity<ResumeRef> saveTailoredResume(@PathVariable long id) {
		ResumeRef saved = this.tailored.saveAsResumeVersion(id);
		return ResponseEntity.created(URI.create("/api/resumes/" + saved.id())).body(saved);
	}

	@GetMapping(path = "/api/tailored-resumes/{id}/docx",
			produces = "application/vnd.openxmlformats-officedocument.wordprocessingml.document")
	ResponseEntity<byte[]> tailoredResumeDocx(@PathVariable long id) {
		Document document = this.tailored.document(id);
		return ResponseEntity.ok()
			.contentType(DOCX)
			.header(HttpHeaders.CONTENT_DISPOSITION,
					ContentDisposition.attachment().filename(document.fileName()).build().toString())
			.body(document.content());
	}

	/** @param baseResumeId currículo de partida; sem ele, o padrão */
	record TailorRequest(@Nullable Long baseResumeId) {
	}

	record TailoredContent(@NotBlank String content) {
	}

}
