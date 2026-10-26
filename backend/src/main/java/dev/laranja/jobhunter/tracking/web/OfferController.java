package dev.laranja.jobhunter.tracking.web;

import java.util.List;

import dev.laranja.jobhunter.tracking.NegotiationDraft;
import dev.laranja.jobhunter.tracking.OfferCommand;
import dev.laranja.jobhunter.tracking.OfferView;
import dev.laranja.jobhunter.tracking.Offers;
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

/** Propostas de uma candidatura, a comparação entre todas e o rascunho da negociação. */
@RestController
class OfferController {

	private final Offers offers;

	OfferController(Offers offers) {
		this.offers = offers;
	}

	@GetMapping("/api/offers")
	List<OfferView> all() {
		return this.offers.all();
	}

	@GetMapping("/api/applications/{applicationId}/offers")
	List<OfferView> forApplication(@PathVariable long applicationId) {
		return this.offers.forApplication(applicationId);
	}

	@PostMapping("/api/applications/{applicationId}/offers")
	@ResponseStatus(HttpStatus.CREATED)
	OfferView create(@PathVariable long applicationId, @Valid @RequestBody OfferCommand command) {
		return this.offers.create(applicationId, command);
	}

	@PutMapping("/api/offers/{id}")
	OfferView update(@PathVariable long id, @Valid @RequestBody OfferCommand command) {
		return this.offers.update(id, command);
	}

	@DeleteMapping("/api/offers/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void delete(@PathVariable long id) {
		this.offers.delete(id);
	}

	/** Texto para você revisar e enviar; nada é guardado nem enviado. */
	@PostMapping("/api/offers/{id}/negotiation-draft")
	NegotiationDraft negotiationDraft(@PathVariable long id, @Valid @RequestBody NegotiationDraft.Request request) {
		return this.offers.negotiationDraft(id, request);
	}

}
