package dev.laranja.jobhunter.interviews.web;

import java.util.List;

import dev.laranja.jobhunter.interviews.StarStories;
import dev.laranja.jobhunter.interviews.StarStoryCommand;
import dev.laranja.jobhunter.interviews.StarStoryPolish;
import dev.laranja.jobhunter.interviews.StarStoryView;
import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Banco de histórias STAR. */
@RestController
class StoryController {

	private final StarStories stories;

	StoryController(StarStories stories) {
		this.stories = stories;
	}

	@GetMapping("/api/stories")
	List<StarStoryView> listStories() {
		return this.stories.list();
	}

	@GetMapping("/api/stories/{id}")
	StarStoryView getStory(@PathVariable long id) {
		return this.stories.get(id);
	}

	@PostMapping("/api/stories")
	@ResponseStatus(HttpStatus.CREATED)
	StarStoryView createStory(@Valid @RequestBody StarStoryCommand command) {
		return this.stories.create(command);
	}

	@PutMapping("/api/stories/{id}")
	StarStoryView updateStory(@PathVariable long id, @Valid @RequestBody StarStoryCommand command) {
		return this.stories.update(id, command);
	}

	@DeleteMapping("/api/stories/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void deleteStory(@PathVariable long id) {
		this.stories.delete(id);
	}

	/** Sugere uma versão lapidada do texto enviado; nada é salvo até você aceitar e salvar. */
	@PostMapping("/api/stories/polish")
	StarStoryPolish polishStory(@Valid @RequestBody StarStoryCommand command) {
		return this.stories.polish(command);
	}

}
