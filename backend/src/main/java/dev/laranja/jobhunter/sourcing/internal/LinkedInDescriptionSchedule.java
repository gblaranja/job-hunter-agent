package dev.laranja.jobhunter.sourcing.internal;

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Uma descrição do LinkedIn por intervalo (1 minuto por padrão): devagar o bastante para não ser bloqueado. */
@Component
@ConditionalOnProperty(name = "jobhunter.sourcing.scheduler-enabled", havingValue = "true", matchIfMissing = true)
class LinkedInDescriptionSchedule {

	private final LinkedInDescriptions descriptions;

	LinkedInDescriptionSchedule(LinkedInDescriptions descriptions) {
		this.descriptions = descriptions;
	}

	@Scheduled(fixedDelayString = "${jobhunter.sourcing.linkedin-descriptions.interval:PT1M}",
			initialDelayString = "PT2M")
	@SchedulerLock(name = "linkedin-descriptions", lockAtMostFor = "PT5M")
	void fetchNext() {
		this.descriptions.fetchNext();
	}

}
