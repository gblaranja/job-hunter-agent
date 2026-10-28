package dev.laranja.jobhunter.sourcing;

import dev.laranja.jobhunter.jobs.JobDismissalService;
import dev.laranja.jobhunter.profile.JobFamiliesChanged;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/**
 * As famílias de cargo dos perfis de busca mudaram: as vagas da caixa de outra família saem com o motivo "sem perfil
 * técnico", como as novas da coleta. Ampliar as famílias não devolve nada sozinho; desfazer é vaga a vaga.
 */
@Component
class JobFamilyFilterListener {

	private static final Logger log = LoggerFactory.getLogger(JobFamilyFilterListener.class);

	private final JobDismissalService dismissals;

	JobFamilyFilterListener(JobDismissalService dismissals) {
		this.dismissals = dismissals;
	}

	@ApplicationModuleListener
	void on(JobFamiliesChanged event) {
		int dismissed = this.dismissals.dismissOutsideFamilies(event.accepted());
		if (dismissed > 0) {
			log.info("{} vagas saíram da caixa por família de cargo fora dos perfis de busca.", dismissed);
		}
	}

}
