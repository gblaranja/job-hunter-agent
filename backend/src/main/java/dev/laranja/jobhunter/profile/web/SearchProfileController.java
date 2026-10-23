package dev.laranja.jobhunter.profile.web;

import java.util.List;

import dev.laranja.jobhunter.profile.SearchProfileCommand;
import dev.laranja.jobhunter.profile.SearchProfileView;
import dev.laranja.jobhunter.profile.SearchProfiles;
import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/search-profiles")
class SearchProfileController {

	private final SearchProfiles profiles;

	SearchProfileController(SearchProfiles profiles) {
		this.profiles = profiles;
	}

	@GetMapping
	List<SearchProfileView> list() {
		return this.profiles.list();
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	SearchProfileView create(@Valid @RequestBody SearchProfileCommand command) {
		return this.profiles.create(command);
	}

	@PutMapping("/{id}")
	SearchProfileView update(@PathVariable long id, @Valid @RequestBody SearchProfileCommand command) {
		return this.profiles.update(id, command);
	}

	@DeleteMapping("/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void delete(@PathVariable long id) {
		this.profiles.delete(id);
	}

}
