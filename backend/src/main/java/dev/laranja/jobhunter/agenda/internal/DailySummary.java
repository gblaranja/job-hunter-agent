package dev.laranja.jobhunter.agenda.internal;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import dev.laranja.jobhunter.agenda.TodayAgenda;
import dev.laranja.jobhunter.agenda.TodayQueries;
import dev.laranja.jobhunter.shared.NotificationRequested;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Resumo do dia às 7h30, depois da coleta das 7h: as 5 melhores vagas para aplicar, os follow-ups vencidos e os
 * e-mails para revisar. Vai para o sino e, como RESUMO_DIARIO, para o Telegram. Sem nada para fazer, não manda nada.
 */
@Component
public class DailySummary {

	static final int TOP_JOBS = 5;

	private final TodayQueries today;

	private final ApplicationEventPublisher events;

	DailySummary(TodayQueries today, ApplicationEventPublisher events) {
		this.today = today;
		this.events = events;
	}

	@Scheduled(cron = "${jobhunter.agenda.daily-summary-cron:0 30 7 * * *}", zone = "America/Sao_Paulo")
	@SchedulerLock(name = "daily-summary", lockAtMostFor = "PT10M")
	void scheduled() {
		send();
	}

	/** @return se havia algo para resumir */
	@Transactional
	public boolean send() {
		TodayAgenda agenda = this.today.today();
		String body = body(agenda);
		if (body.isEmpty()) {
			return false;
		}
		int jobs = agenda.jobs().size();
		String title = (jobs == 0) ? "Seu dia: nenhuma vaga nova para aplicar"
				: "Seu dia: " + jobs + ((jobs == 1) ? " vaga para aplicar" : " vagas para aplicar");
		this.events.publishEvent(new NotificationRequested("RESUMO_DIARIO", title, body, "/today",
				"resumo-diario-" + agenda.day()));
		return true;
	}

	static String body(TodayAgenda agenda) {
		List<String> sections = new ArrayList<>();
		if (!agenda.jobs().isEmpty()) {
			sections.add("Vagas:\n" + agenda.jobs()
				.stream()
				.limit(TOP_JOBS)
				.map(job -> "- " + job.title() + " · " + job.company() + " (" + job.matchScore() + "%)")
				.collect(Collectors.joining("\n")));
		}
		if (!agenda.followUps().isEmpty()) {
			sections.add("Follow-ups vencidos: " + agenda.followUps()
				.stream()
				.map(TodayAgenda.TodayFollowUp::company)
				.distinct()
				.collect(Collectors.joining(", ")));
		}
		if (!agenda.emails().isEmpty()) {
			int emails = agenda.emails().size();
			sections.add((emails == 1) ? "1 e-mail para revisar" : emails + " e-mails para revisar");
		}
		if (sections.isEmpty()) {
			return "";
		}
		TodayAgenda.TodayWeeklyGoal goal = agenda.weeklyGoal();
		sections.add("Meta da semana: " + goal.applied() + " de " + goal.goal() + " candidaturas");
		return String.join("\n\n", sections);
	}

}
