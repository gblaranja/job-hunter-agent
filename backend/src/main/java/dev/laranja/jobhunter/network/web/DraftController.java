package dev.laranja.jobhunter.network.web;

import dev.laranja.jobhunter.network.MessageDraft;
import dev.laranja.jobhunter.network.MessageDrafts;
import jakarta.validation.Valid;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
class DraftController {

	private final MessageDrafts drafts;

	DraftController(MessageDrafts drafts) {
		this.drafts = drafts;
	}

	/** Rascunho de pedido de indicação, follow-up, agradecimento ou resposta a recrutador. Nada é enviado. */
	@PostMapping("/api/jobs/{jobId}/drafts")
	MessageDraft draftMessage(@PathVariable long jobId, @Valid @RequestBody MessageDrafts.DraftRequest request) {
		return this.drafts.draft(jobId, request);
	}

}
