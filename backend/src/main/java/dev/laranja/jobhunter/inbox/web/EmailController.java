package dev.laranja.jobhunter.inbox.web;

import java.util.List;

import dev.laranja.jobhunter.inbox.EmailClassification;
import dev.laranja.jobhunter.inbox.EmailReview;
import dev.laranja.jobhunter.inbox.EmailReview.Accuracy;
import dev.laranja.jobhunter.inbox.EmailReview.EmailItem;
import jakarta.validation.Valid;
import org.jspecify.annotations.Nullable;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/emails")
class EmailController {

	private final EmailReview review;

	EmailController(EmailReview review) {
		this.review = review;
	}

	/** @param pending só a fila de revisão (padrão) */
	@GetMapping
	List<EmailItem> list(@RequestParam(defaultValue = "true") boolean pending,
			@RequestParam(required = false) @Nullable EmailClassification classification) {
		return this.review.list(pending, classification);
	}

	@GetMapping("/accuracy")
	Accuracy accuracy() {
		return this.review.accuracy();
	}

	@PatchMapping("/{id}/classification")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void review(@PathVariable long id, @Valid @RequestBody EmailReview.Review review) {
		this.review.review(id, review);
	}

}
