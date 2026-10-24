package dev.laranja.jobhunter.jobs.web;

import java.net.URI;

import dev.laranja.jobhunter.jobs.FeedbackRating;
import dev.laranja.jobhunter.jobs.JobCatalog;
import dev.laranja.jobhunter.jobs.JobDismissalService;
import dev.laranja.jobhunter.jobs.JobFeedbackService;
import dev.laranja.jobhunter.jobs.JobScope;
import dev.laranja.jobhunter.jobs.JobView;
import dev.laranja.jobhunter.jobs.ManualJobCommand;
import dev.laranja.jobhunter.shared.web.PageResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/jobs")
class JobController {

	private final JobCatalog catalog;

	private final JobQueries queries;

	private final JobFeedbackService feedback;

	private final JobDismissalService dismissals;

	JobController(JobCatalog catalog, JobQueries queries, JobFeedbackService feedback,
			JobDismissalService dismissals) {
		this.catalog = catalog;
		this.queries = queries;
		this.feedback = feedback;
		this.dismissals = dismissals;
	}

	@PostMapping
	ResponseEntity<JobView> create(@Valid @RequestBody ManualJobCommand command) {
		JobView job = this.catalog.registerManual(command);
		return ResponseEntity.created(URI.create("/api/jobs/" + job.id())).body(job);
	}

	/**
	 * Lista paginada de vagas.
	 * @param scope só as vagas nacionais ou só as internacionais
	 * @param inbox só as vagas da caixa: sem candidatura, sem "não gostei" e não descartadas
	 */
	@GetMapping
	PageResponse<JobListItem> list(@RequestParam(required = false) @Pattern(regexp = "[A-Z_]+") String status,
			@RequestParam(required = false) FeedbackRating rating, @RequestParam(required = false) String q,
			@RequestParam(required = false) JobScope scope, @RequestParam(defaultValue = "false") boolean inbox,
			@RequestParam(defaultValue = "0") @Min(0) int page,
			@RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
		return this.queries.list(new JobQueries.JobListFilter(status, rating, q, scope, inbox), page, size);
	}

	/** Corrige a classificação automática de nacional ou internacional: a vaga muda de seção na caixa. */
	@PutMapping("/{id}/scope")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void scope(@PathVariable long id, @Valid @RequestBody ScopeChange change) {
		this.catalog.changeScope(id, change.scope());
	}

	@PutMapping("/{id}/feedback")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void feedback(@PathVariable long id, @Valid @RequestBody JobFeedbackService.Feedback feedback) {
		this.feedback.save(id, feedback);
	}

	/** "Não tenho interesse" ou "não tenho o perfil técnico": a vaga sai da caixa. */
	@PutMapping("/{id}/dismissal")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void dismiss(@PathVariable long id, @Valid @RequestBody JobDismissalService.Dismissal dismissal) {
		this.dismissals.dismiss(id, dismissal);
	}

	/** Desfaz o descarte e a vaga volta para a caixa. */
	@DeleteMapping("/{id}/dismissal")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void restore(@PathVariable long id) {
		this.dismissals.restore(id);
	}

	@GetMapping("/{id}")
	JobDetail detail(@PathVariable long id) {
		return this.queries.detail(this.catalog.get(id));
	}

	record ScopeChange(@NotNull JobScope scope) {
	}

}
