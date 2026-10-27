package dev.laranja.jobhunter.analysis.web;

import java.util.List;

import dev.laranja.jobhunter.analysis.SkillReview;
import dev.laranja.jobhunter.analysis.SkillReview.ReviewedSkill;
import dev.laranja.jobhunter.analysis.SkillReview.SkillAcceptance;
import dev.laranja.jobhunter.analysis.SkillReview.SkillSuggestion;
import dev.laranja.jobhunter.analysis.internal.EscoClient;
import dev.laranja.jobhunter.analysis.internal.EscoClient.EscoSkill;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** Revisão do dicionário de skills, em Configurações. */
@RestController
@RequestMapping("/api/skills")
class SkillController {

	private final SkillReview review;

	private final EscoClient esco;

	SkillController(SkillReview review, EscoClient esco) {
		this.review = review;
		this.esco = esco;
	}

	/**
	 * Skills pedidas nas vagas que o dicionário não conhece.
	 * @param minJobs mínimo de vagas que pedem a skill
	 */
	@GetMapping("/suggestions")
	List<SkillSuggestion> suggestions(
			@RequestParam(defaultValue = "" + SkillReview.DEFAULT_MIN_JOBS) @Min(1) @Max(100) int minJobs) {
		return this.review.suggestions(minJobs);
	}

	/** Skills aceitas na revisão, que complementam o {@code skills.txt}. */
	@GetMapping("/reviewed")
	List<ReviewedSkill> reviewed() {
		return this.review.reviewed();
	}

	/** Aceita uma sugestão: a skill passa a contar no score determinístico. */
	@PostMapping("/reviewed")
	@ResponseStatus(HttpStatus.CREATED)
	ReviewedSkill accept(@Valid @RequestBody SkillAcceptance acceptance) {
		return this.review.accept(acceptance);
	}

	@DeleteMapping("/reviewed/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void remove(@PathVariable long id) {
		this.review.remove(id);
	}

	/** Não sugere mais a skill. */
	@PostMapping("/ignored")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void ignore(@Valid @RequestBody SkillIgnore ignore) {
		this.review.ignore(ignore.name());
	}

	/** Procura a skill na ESCO, para gravar a URI e os rótulos em português e inglês. */
	@GetMapping("/esco")
	List<EscoSkill> esco(@RequestParam @NotBlank @Size(max = 100) String text) {
		try {
			return this.esco.search(text);
		}
		catch (EscoClient.EscoUnavailableException ex) {
			throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, ex.getMessage(), ex);
		}
	}

	record SkillIgnore(@NotBlank @Size(max = 100) String name) {
	}

}
