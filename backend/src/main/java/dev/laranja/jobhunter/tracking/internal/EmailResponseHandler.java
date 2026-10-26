package dev.laranja.jobhunter.tracking.internal;

import java.util.List;

import dev.laranja.jobhunter.inbox.EmailClassificationCorrected;
import dev.laranja.jobhunter.inbox.EmailClassified;
import dev.laranja.jobhunter.inbox.Mailbox;
import dev.laranja.jobhunter.tracking.ApplicationService;
import dev.laranja.jobhunter.tracking.ResponseType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/**
 * Liga os retornos classificados às candidaturas. Com confiança de pelo menos {@value #CONFIDENCE_THRESHOLD} e uma
 * única candidatura correspondente, o card se move sozinho; nos demais casos, o e-mail espera a sua revisão.
 */
@Component
class EmailResponseHandler {

	static final double CONFIDENCE_THRESHOLD = 0.7;

	private static final Logger log = LoggerFactory.getLogger(EmailResponseHandler.class);

	private final ApplicationMatcher matcher;

	private final ApplicationService applications;

	private final Mailbox mailbox;

	EmailResponseHandler(ApplicationMatcher matcher, ApplicationService applications, Mailbox mailbox) {
		this.matcher = matcher;
		this.applications = applications;
		this.mailbox = mailbox;
	}

	@ApplicationModuleListener
	void on(EmailClassified event) {
		if (!event.classification().isResponse()) {
			return;
		}
		List<Long> candidates = this.matcher.match(event.companyGuess(), event.sender());
		if (candidates.size() != 1) {
			log.info("Retorno {} sem candidatura única ({} encontradas); fica para revisão.", event.emailId(), candidates.size());
			return;
		}
		long applicationId = candidates.getFirst();
		if (event.confidence() < CONFIDENCE_THRESHOLD) {
			this.mailbox.suggestApplication(event.emailId(), applicationId);
			return;
		}
		this.applications.recordEmailResponse(applicationId, ResponseType.valueOf(event.classification().name()),
				event.emailId(), (event.subject() != null) ? event.subject() : "Retorno por e-mail");
		this.mailbox.markHandled(event.emailId(), applicationId);
	}

	@ApplicationModuleListener
	void on(EmailClassificationCorrected event) {
		ResponseType type = event.classification().isResponse() ? ResponseType.valueOf(event.classification().name()) : null;
		this.applications.applyEmailCorrection(event.emailId(), type, event.applicationId());
	}

}
