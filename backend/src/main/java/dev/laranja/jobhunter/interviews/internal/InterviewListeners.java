package dev.laranja.jobhunter.interviews.internal;

import dev.laranja.jobhunter.interviews.InterviewPreps;
import dev.laranja.jobhunter.tracking.ApplicationStatus;
import dev.laranja.jobhunter.tracking.ApplicationStatusChanged;

import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/** Gera a preparação quando a candidatura chega a Entrevista ou Teste técnico (uma vez por etapa). */
@Component
class InterviewListeners {

	private final InterviewPreps preps;

	private final PrepGenerator generator;

	InterviewListeners(InterviewPreps preps, PrepGenerator generator) {
		this.preps = preps;
		this.generator = generator;
	}

	@ApplicationModuleListener
	void on(ApplicationStatusChanged event) {
		if ((event.to() == ApplicationStatus.ENTREVISTA || event.to() == ApplicationStatus.TESTE_TECNICO)
				&& !this.preps.exists(event.applicationId(), event.to())) {
			this.preps.request(event.applicationId(), event.to());
		}
	}

	@ApplicationModuleListener
	void on(InterviewPrepRequested event) {
		this.generator.generate(event.prepId());
	}

}
