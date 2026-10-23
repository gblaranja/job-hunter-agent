package dev.laranja.jobhunter.notifications.internal;

import dev.laranja.jobhunter.analysis.JobAnalyzed;
import dev.laranja.jobhunter.companies.CompanyAlertRaised;
import dev.laranja.jobhunter.jobs.JobCatalog;
import dev.laranja.jobhunter.jobs.JobView;
import dev.laranja.jobhunter.notifications.Notifications;
import dev.laranja.jobhunter.tailoring.TailoredResumeReady;

import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/** Transforma eventos dos outros módulos em notificações. */
@Component
class NotificationTriggers {

	/** Vagas a partir deste score viram notificação. */
	static final int HIGH_SCORE = 75;

	private final Notifications notifications;

	private final JobCatalog jobs;

	NotificationTriggers(Notifications notifications, JobCatalog jobs) {
		this.notifications = notifications;
		this.jobs = jobs;
	}

	@ApplicationModuleListener
	void on(JobAnalyzed event) {
		if (event.matchScore() == null || event.matchScore() < HIGH_SCORE) {
			return;
		}
		JobView job = this.jobs.get(event.jobId());
		this.notifications.notify("VAGA_ADERENTE", "Vaga com " + event.matchScore() + "% de aderência: " + job.title(),
				job.company(), "/jobs/" + job.id(), "vaga-aderente-" + job.id());
	}

	@ApplicationModuleListener
	void on(TailoredResumeReady event) {
		JobView job = this.jobs.get(event.jobId());
		this.notifications.notify("CURRICULO_ADAPTADO", "Currículo adaptado para " + job.title(),
				job.company() + " · " + event.coveredKeywords() + " de " + event.keywords()
						+ " palavras-chave da vaga no texto",
				"/jobs/" + job.id(), "curriculo-adaptado-" + event.tailoredResumeId());
	}

	@ApplicationModuleListener
	void on(CompanyAlertRaised event) {
		this.notifications.notify(event.type(), event.title(), event.body(), "/companies/" + event.companyId(),
				event.dedupKey());
	}

}
