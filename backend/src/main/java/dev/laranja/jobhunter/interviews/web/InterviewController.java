package dev.laranja.jobhunter.interviews.web;

import java.util.List;

import dev.laranja.jobhunter.interviews.InterviewDebriefCommand;
import dev.laranja.jobhunter.interviews.InterviewDebriefView;
import dev.laranja.jobhunter.interviews.InterviewDebriefs;
import dev.laranja.jobhunter.interviews.InterviewPrepView;
import dev.laranja.jobhunter.interviews.InterviewPreps;
import dev.laranja.jobhunter.tracking.ApplicationStatus;
import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Preparação e registros pós-entrevista de uma candidatura. */
@RestController
class InterviewController {

	private final InterviewPreps preps;

	private final InterviewDebriefs debriefs;

	InterviewController(InterviewPreps preps, InterviewDebriefs debriefs) {
		this.preps = preps;
		this.debriefs = debriefs;
	}

	/** As preparações da candidatura, a mais recente primeiro. */
	@GetMapping("/api/applications/{id}/interview-prep")
	List<InterviewPrepView> interviewPreps(@PathVariable long id) {
		return this.preps.forApplication(id);
	}

	/** Gera (ou refaz) a preparação; sem etapa, usa a atual da candidatura. */
	@PostMapping("/api/applications/{id}/interview-prep")
	@ResponseStatus(HttpStatus.ACCEPTED)
	InterviewPrepView requestInterviewPrep(@PathVariable long id, @RequestParam(required = false) ApplicationStatus stage) {
		return this.preps.request(id, stage);
	}

	@GetMapping("/api/applications/{id}/debriefs")
	List<InterviewDebriefView> interviewDebriefs(@PathVariable long id) {
		return this.debriefs.forApplication(id);
	}

	@PostMapping("/api/applications/{id}/debriefs")
	@ResponseStatus(HttpStatus.CREATED)
	InterviewDebriefView.Saved createDebrief(@PathVariable long id, @Valid @RequestBody InterviewDebriefCommand command) {
		return this.debriefs.create(id, command);
	}

	@PutMapping("/api/debriefs/{id}")
	InterviewDebriefView updateDebrief(@PathVariable long id, @Valid @RequestBody InterviewDebriefCommand command) {
		return this.debriefs.update(id, command);
	}

	@DeleteMapping("/api/debriefs/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void deleteDebrief(@PathVariable long id) {
		this.debriefs.delete(id);
	}

	@GetMapping("/api/interviews/recurring-questions")
	List<InterviewDebriefView.RecurringQuestion> recurringInterviewQuestions() {
		return this.debriefs.recurringQuestions();
	}

}
