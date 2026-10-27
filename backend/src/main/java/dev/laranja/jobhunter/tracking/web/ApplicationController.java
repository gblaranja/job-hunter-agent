package dev.laranja.jobhunter.tracking.web;

import java.io.IOException;
import java.net.URI;
import java.util.List;

import dev.laranja.jobhunter.shared.storage.DownloadLink;
import dev.laranja.jobhunter.tracking.ApplicationCommands;
import dev.laranja.jobhunter.tracking.ApplicationService;
import dev.laranja.jobhunter.tracking.ApplicationStatus;
import dev.laranja.jobhunter.tracking.LinkVerification;
import dev.laranja.jobhunter.tracking.web.ApplicationViews.AppliedJob;
import dev.laranja.jobhunter.tracking.web.ApplicationViews.AppliedListItem;
import dev.laranja.jobhunter.tracking.web.ApplicationViews.Board;
import dev.laranja.jobhunter.tracking.web.ApplicationViews.Created;
import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
class ApplicationController {

	private final ApplicationService service;

	private final ApplicationQueries queries;

	private final LinkVerification links;

	ApplicationController(ApplicationService service, ApplicationQueries queries, LinkVerification links) {
		this.service = service;
		this.queries = queries;
		this.links = links;
	}

	/** Verifica agora os links das vagas aplicadas em aberto (normalmente roda toda segunda às 8h). */
	@PostMapping("/api/admin/verify-links")
	LinkVerification.Summary verifyLinks() {
		return this.links.verifyOpenApplications();
	}

	@PostMapping("/api/jobs/{jobId}/application")
	ResponseEntity<Created> start(@PathVariable long jobId, @Valid @RequestBody ApplicationCommands.Start command) {
		long id = this.service.start(jobId, command);
		return ResponseEntity.created(URI.create("/api/applications/" + id + "/snapshot")).body(new Created(id));
	}

	/** Cria a vaga e a candidatura em Aplicada a partir do e-mail de confirmação, com a data do e-mail. */
	@PostMapping("/api/applications/from-email/{emailId}")
	ResponseEntity<Created> registerFromEmail(@PathVariable long emailId,
			@Valid @RequestBody ApplicationCommands.FromEmail command) {
		long id = this.service.registerFromEmail(emailId, command);
		return ResponseEntity.created(URI.create("/api/applications/" + id + "/snapshot")).body(new Created(id));
	}

	@GetMapping("/api/applications/board")
	Board board() {
		return this.queries.board();
	}

	@GetMapping("/api/applications")
	List<AppliedListItem> list(@RequestParam(required = false) ApplicationStatus status,
			@RequestParam(required = false) String q) {
		return this.queries.list(status, q);
	}

	@PatchMapping("/api/applications/{id}/status")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void changeStatus(@PathVariable long id, @Valid @RequestBody ApplicationCommands.ChangeStatus command) {
		this.service.changeStatus(id, command);
	}

	@PostMapping("/api/applications/{id}/responses")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void recordResponse(@PathVariable long id, @Valid @RequestBody ApplicationCommands.RecordResponse command) {
		this.service.recordManualResponse(id, command);
	}

	/** Você mandou um follow-up: conta o envio e agenda o próximo (até o máximo configurado). */
	@PostMapping("/api/applications/{id}/follow-ups")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void registerFollowUp(@PathVariable long id) {
		this.service.registerFollowUp(id);
	}

	@PatchMapping("/api/applications/{id}/resume")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void changeResume(@PathVariable long id, @Valid @RequestBody ApplicationCommands.ChangeResume command) {
		this.service.changeResume(id, command.resumeVersionId());
	}

	@PatchMapping("/api/applications/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void updateDetails(@PathVariable long id, @RequestBody ApplicationCommands.UpdateDetails command) {
		this.service.updateDetails(id, command);
	}

	@GetMapping("/api/applications/{id}/snapshot")
	AppliedJob snapshot(@PathVariable long id) {
		return this.queries.appliedJob(id);
	}

	@PutMapping("/api/applications/{id}/snapshot")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void saveSnapshot(@PathVariable long id, @Valid @RequestBody ApplicationCommands.SaveSnapshot command) {
		this.service.saveSnapshot(id, command);
	}

	@PostMapping(path = "/api/applications/{id}/snapshot/pdf", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void attachPagePdf(@PathVariable long id, @RequestPart("file") MultipartFile file) throws IOException {
		this.service.attachPagePdf(id, file.getBytes());
	}

	@GetMapping("/api/applications/{id}/snapshot/pdf")
	DownloadLink pagePdf(@PathVariable long id) {
		return this.service.pagePdfLink(id);
	}

}
