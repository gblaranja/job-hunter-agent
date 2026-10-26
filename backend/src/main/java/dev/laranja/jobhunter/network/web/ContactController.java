package dev.laranja.jobhunter.network.web;

import java.net.URI;
import java.util.List;

import dev.laranja.jobhunter.network.ContactBook;
import dev.laranja.jobhunter.network.ContactBook.ApplicationContacts;
import dev.laranja.jobhunter.network.ContactCommands;
import dev.laranja.jobhunter.network.ContactInteractionView;
import dev.laranja.jobhunter.network.ContactRole;
import dev.laranja.jobhunter.network.ContactView;
import dev.laranja.jobhunter.shared.web.PageResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.jspecify.annotations.Nullable;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
class ContactController {

	private final ContactBook contacts;

	ContactController(ContactBook contacts) {
		this.contacts = contacts;
	}

	/**
	 * Contatos, os com conversa mais recente primeiro.
	 * @param q parte do nome, da empresa ou do cargo
	 */
	@GetMapping("/api/contacts")
	PageResponse<ContactView> listContacts(@RequestParam(required = false) @Nullable String q,
			@RequestParam(required = false) @Nullable Long companyId,
			@RequestParam(required = false) @Nullable ContactRole role,
			@RequestParam(defaultValue = "0") @Min(0) int page,
			@RequestParam(defaultValue = "50") @Min(1) @Max(200) int size) {
		return this.contacts.list(q, companyId, role, page, size);
	}

	@PostMapping("/api/contacts")
	ResponseEntity<ContactView> createContact(@Valid @RequestBody ContactCommands.Save command) {
		ContactView contact = this.contacts.create(command);
		return ResponseEntity.created(URI.create("/api/contacts/" + contact.id())).body(contact);
	}

	@GetMapping("/api/contacts/{id}")
	ContactView getContact(@PathVariable long id) {
		return this.contacts.get(id);
	}

	@PutMapping("/api/contacts/{id}")
	ContactView updateContact(@PathVariable long id, @Valid @RequestBody ContactCommands.Save command) {
		return this.contacts.update(id, command);
	}

	/** Apaga o contato e as conversas registradas com ele. */
	@DeleteMapping("/api/contacts/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void deleteContact(@PathVariable long id) {
		this.contacts.delete(id);
	}

	@GetMapping("/api/contacts/{id}/interactions")
	List<ContactInteractionView> contactInteractions(@PathVariable long id) {
		return this.contacts.interactions(id);
	}

	@PostMapping("/api/contacts/{id}/interactions")
	@ResponseStatus(HttpStatus.CREATED)
	CreatedContactInteraction recordContactInteraction(@PathVariable long id,
			@Valid @RequestBody ContactCommands.RecordInteraction command) {
		return new CreatedContactInteraction(this.contacts.recordInteraction(id, command));
	}

	/** Quem você conhece na empresa da vaga. */
	@GetMapping("/api/jobs/{jobId}/contacts")
	List<ContactView> jobContacts(@PathVariable long jobId) {
		return this.contacts.forJob(jobId);
	}

	/** Contatos e conversas da candidatura. */
	@GetMapping("/api/applications/{id}/contacts")
	ApplicationContacts applicationContacts(@PathVariable long id) {
		return this.contacts.forApplication(id);
	}

	record CreatedContactInteraction(long id) {
	}

}
