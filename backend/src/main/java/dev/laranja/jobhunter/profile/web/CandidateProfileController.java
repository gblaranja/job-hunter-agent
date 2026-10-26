package dev.laranja.jobhunter.profile.web;

import dev.laranja.jobhunter.profile.CandidateProfileCommand;
import dev.laranja.jobhunter.profile.CandidateProfileView;
import dev.laranja.jobhunter.profile.CandidateProfiles;
import jakarta.validation.Valid;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Dados da pessoa para a triagem: comparados com os eliminatórios de cada vaga. */
@RestController
@RequestMapping("/api/candidate-profile")
class CandidateProfileController {

	private final CandidateProfiles profiles;

	CandidateProfileController(CandidateProfiles profiles) {
		this.profiles = profiles;
	}

	@GetMapping
	CandidateProfileView candidateProfile() {
		return this.profiles.current();
	}

	@PutMapping
	CandidateProfileView updateCandidateProfile(@Valid @RequestBody CandidateProfileCommand command) {
		return this.profiles.update(command);
	}

}
