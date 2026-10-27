package dev.laranja.jobhunter.tracking.internal;

import java.util.List;

import dev.laranja.jobhunter.inbox.EmailClassification;
import dev.laranja.jobhunter.inbox.EmailClassified;
import dev.laranja.jobhunter.inbox.Mailbox;
import dev.laranja.jobhunter.tracking.ApplicationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/**
 * Liga os e-mails "recebemos sua candidatura" às candidaturas. Já aplicada: confirma e completa a data. Em
 * Interesse: sugere mover para Aplicada na sua revisão (falta o currículo enviado). Sem candidatura: o e-mail fica na
 * revisão como "candidatura não registrada", para você criar com um clique.
 */
@Component
class ApplicationConfirmationHandler {

	private static final Logger log = LoggerFactory.getLogger(ApplicationConfirmationHandler.class);

	private final ApplicationMatcher matcher;

	private final ApplicationService applications;

	private final Mailbox mailbox;

	ApplicationConfirmationHandler(ApplicationMatcher matcher, ApplicationService applications, Mailbox mailbox) {
		this.matcher = matcher;
		this.applications = applications;
		this.mailbox = mailbox;
	}

	@ApplicationModuleListener
	void on(EmailClassified event) {
		if (event.classification() != EmailClassification.CONFIRMACAO_CANDIDATURA) {
			return;
		}
		List<Long> candidates = this.matcher.match(event.companyGuess(), event.sender());
		if (candidates.size() != 1) {
			log.info("Confirmação {} sem candidatura única ({} encontradas); fica para revisão.", event.emailId(),
					candidates.size());
			return;
		}
		long applicationId = candidates.getFirst();
		if (this.applications.confirmFromEmail(applicationId, event.receivedAt())) {
			this.mailbox.markHandled(event.emailId(), applicationId);
		}
		else {
			this.mailbox.suggestApplication(event.emailId(), applicationId);
		}
	}

}
