package dev.laranja.jobhunter.tracking.internal;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import dev.laranja.jobhunter.tracking.FollowUpsDue;
import dev.laranja.jobhunter.tracking.FollowUpsDue.DueAction;
import dev.laranja.jobhunter.tracking.NextActionKind;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Lembrete diário das próximas ações vencidas (follow-ups, pedidos a responder): publica {@link FollowUpsDue}, que
 * vira uma notificação por dia com a lista. Nada é enviado à empresa.
 */
@Component
class FollowUpReminder {

	private static final Logger log = LoggerFactory.getLogger(FollowUpReminder.class);

	private static final ZoneId ZONE = ZoneId.of("America/Sao_Paulo");

	private final JdbcClient jdbc;

	private final ApplicationEventPublisher events;

	private final Clock clock;

	FollowUpReminder(JdbcClient jdbc, ApplicationEventPublisher events, Clock clock) {
		this.jdbc = jdbc;
		this.events = events;
		this.clock = clock;
	}

	@Scheduled(cron = "${jobhunter.tracking.follow-up-cron:0 0 8 * * *}", zone = "America/Sao_Paulo")
	@SchedulerLock(name = "follow-up-reminder", lockAtMostFor = "PT10M")
	void daily() {
		int due = remind();
		log.info("{} próximas ações vencidas nas candidaturas.", due);
	}

	/** @return quantas próximas ações estão vencidas */
	@Transactional
	public int remind() {
		Instant now = this.clock.instant();
		List<DueAction> due = this.jdbc.sql("""
				select a.id, coalesce(s.company, j.company) as company, coalesce(a.next_action_kind, 'OUTRO') as kind
				from application a
				join job j on j.id = a.job_id
				left join job_snapshot s on s.application_id = a.id
				where a.next_action_at <= :now and a.status not in ('RECUSADA', 'DESISTI', 'ENCERRADA')
				order by a.next_action_at
				""")
			.param("now", Timestamp.from(now))
			.query((rs, row) -> new DueAction(rs.getLong("id"), rs.getString("company"),
					NextActionKind.valueOf(rs.getString("kind"))))
			.list();
		if (!due.isEmpty()) {
			this.events.publishEvent(new FollowUpsDue(LocalDate.ofInstant(now, ZONE), due));
		}
		return due.size();
	}

}
