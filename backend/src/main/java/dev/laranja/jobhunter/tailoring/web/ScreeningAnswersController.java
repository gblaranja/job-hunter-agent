package dev.laranja.jobhunter.tailoring.web;

import java.util.List;

import dev.laranja.jobhunter.tailoring.ScreeningAnswers;
import dev.laranja.jobhunter.tailoring.ScreeningAnswers.ScreeningAnswersView;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import org.jspecify.annotations.Nullable;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
class ScreeningAnswersController {

	private final ScreeningAnswers answers;

	ScreeningAnswersController(ScreeningAnswers answers) {
		this.answers = answers;
	}

	/** Rascunhos das respostas do formulário da vaga e, se pedida, da carta curta. Nada é enviado. */
	@PostMapping("/api/jobs/{id}/screening-answers")
	ScreeningAnswersView screeningAnswers(@PathVariable long id, @Valid @RequestBody ScreeningQuestions request) {
		return this.answers.answer(id, (request.questions() == null) ? List.of() : request.questions(),
				request.coverLetter());
	}

	/**
	 * @param questions as perguntas do formulário, uma por item
	 * @param coverLetter pede também a carta de apresentação curta
	 */
	record ScreeningQuestions(@Nullable @Size(max = 15) List<@Size(max = 1000) String> questions, boolean coverLetter) {
	}

}
