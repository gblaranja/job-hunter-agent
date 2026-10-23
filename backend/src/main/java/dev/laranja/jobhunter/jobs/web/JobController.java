package dev.laranja.jobhunter.jobs.web;

import java.net.URI;

import dev.laranja.jobhunter.jobs.JobCatalog;
import dev.laranja.jobhunter.jobs.JobView;
import dev.laranja.jobhunter.jobs.ManualJobCommand;
import dev.laranja.jobhunter.shared.web.PageResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/jobs")
class JobController {

	private final JobCatalog catalog;

	private final JobQueries queries;

	JobController(JobCatalog catalog, JobQueries queries) {
		this.catalog = catalog;
		this.queries = queries;
	}

	@PostMapping
	ResponseEntity<JobView> create(@Valid @RequestBody ManualJobCommand command) {
		JobView job = this.catalog.registerManual(command);
		return ResponseEntity.created(URI.create("/api/jobs/" + job.id())).body(job);
	}

	@GetMapping
	PageResponse<JobListItem> list(@RequestParam(required = false) @Pattern(regexp = "[A-Z_]+") String status,
			@RequestParam(required = false) String q, @RequestParam(defaultValue = "0") @Min(0) int page,
			@RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
		return this.queries.list(status, q, page, size);
	}

	@GetMapping("/{id}")
	JobDetail detail(@PathVariable long id) {
		return this.queries.detail(this.catalog.get(id));
	}

}
