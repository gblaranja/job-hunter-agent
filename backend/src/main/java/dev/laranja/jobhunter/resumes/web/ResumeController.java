package dev.laranja.jobhunter.resumes.web;

import java.io.IOException;
import java.net.URI;
import java.util.List;

import dev.laranja.jobhunter.resumes.internal.ResumeService;
import dev.laranja.jobhunter.resumes.internal.ResumeService.UpdateResumeCommand;
import dev.laranja.jobhunter.shared.storage.DownloadLink;
import jakarta.validation.constraints.Size;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/resumes")
class ResumeController {

	private final ResumeService service;

	private final ResumeQueries queries;

	ResumeController(ResumeService service, ResumeQueries queries) {
		this.service = service;
		this.queries = queries;
	}

	@PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	ResponseEntity<ResumeItem> upload(@RequestPart("file") MultipartFile file,
			@RequestParam(required = false) @Size(max = 120) String label,
			@RequestParam(required = false) @Size(max = 120) String targetRole) throws IOException {
		long id = this.service.upload(file.getBytes(), file.getOriginalFilename(), label, targetRole);
		return ResponseEntity.created(URI.create("/api/resumes/" + id)).body(this.queries.get(id));
	}

	@GetMapping
	List<ResumeItem> list() {
		return this.queries.list();
	}

	@GetMapping("/{id}/file")
	DownloadLink file(@PathVariable long id) {
		return this.service.downloadLink(id);
	}

	@PatchMapping("/{id}")
	ResumeItem update(@PathVariable long id, @RequestBody UpdateResumeCommand command) {
		this.service.update(id, command);
		return this.queries.get(id);
	}

}
