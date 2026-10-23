package dev.laranja.jobhunter.tailoring.internal;

import dev.laranja.jobhunter.shared.BusinessRuleException;
import dev.laranja.jobhunter.shared.ConflictException;
import dev.laranja.jobhunter.tailoring.TailoredResumes;
import dev.laranja.jobhunter.tailoring.TailoringProperties;
import dev.laranja.jobhunter.tracking.ApplicationStatus;
import dev.laranja.jobhunter.tracking.ApplicationStatusChanged;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/** Roda a reescrita pedida e, com a geração automática ligada, pede uma versão quando você marca interesse na vaga. */
@Component
class TailoringListeners {

	private static final Logger log = LoggerFactory.getLogger(TailoringListeners.class);

	private final TailoredResumes tailored;

	private final ResumeTailor tailor;

	private final TailoringProperties properties;

	TailoringListeners(TailoredResumes tailored, ResumeTailor tailor, TailoringProperties properties) {
		this.tailored = tailored;
		this.tailor = tailor;
		this.properties = properties;
	}

	@ApplicationModuleListener
	void on(TailoringRequested event) {
		this.tailored.generate(event.tailoredResumeId());
	}

	/** Só na primeira vez: as versões seguintes são pedidas na tela da vaga. */
	@ApplicationModuleListener
	void on(ApplicationStatusChanged event) {
		if (event.to() != ApplicationStatus.INTERESSE || !this.properties.autoGenerate() || !this.tailor.enabled()
				|| this.tailored.existsForJob(event.jobId())) {
			return;
		}
		try {
			this.tailored.request(event.jobId(), null);
		}
		catch (BusinessRuleException | ConflictException ex) {
			log.info("Currículo adaptado não gerado para a vaga {}: {}", event.jobId(), ex.getMessage());
		}
	}

}
