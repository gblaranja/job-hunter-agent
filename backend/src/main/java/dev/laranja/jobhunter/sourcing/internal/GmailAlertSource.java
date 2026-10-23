package dev.laranja.jobhunter.sourcing.internal;

import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

import dev.laranja.jobhunter.inbox.Mailbox;
import dev.laranja.jobhunter.inbox.Mailbox.AlertEmail;
import dev.laranja.jobhunter.jobs.RawJob;
import dev.laranja.jobhunter.sourcing.JobSource;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.stereotype.Component;

/**
 * Vagas dos alertas por e-mail (LinkedIn, Gupy), lidos da label do Gmail. É a fonte principal: cobre o
 * LinkedIn sem automação na conta.
 */
@Component
class GmailAlertSource implements JobSource {

	private static final Logger log = LoggerFactory.getLogger(GmailAlertSource.class);

	private static final int BATCH = 200;

	private final Mailbox mailbox;

	private final List<AlertParser> parsers;

	private final MeterRegistry meters;

	GmailAlertSource(Mailbox mailbox, List<AlertParser> parsers, MeterRegistry meters) {
		this.mailbox = mailbox;
		this.parsers = parsers;
		this.meters = meters;
	}

	@Override
	public String id() {
		return "GMAIL_ALERTAS";
	}

	@Override
	public boolean available() {
		return this.mailbox.connected();
	}

	@Override
	public void collect(Consumer<RawJob> sink) {
		for (AlertEmail alert : this.mailbox.pendingAlerts(BATCH)) {
			Optional<AlertParser> parser = this.parsers.stream().filter(p -> p.supports(alert.sender())).findFirst();
			List<RawJob> jobs = parser.map(p -> p.parse(alert.html())).orElse(List.of());
			if (jobs.isEmpty()) {
				// Alerta sem vagas reconhecidas: provavelmente o layout do e-mail mudou e o parser precisa de ajuste.
				log.warn("Nenhuma vaga reconhecida no alerta de {} (\"{}\").", alert.sender(), alert.subject());
				this.meters.counter("jobhunter.alerts.unparsed", "sender", alert.sender()).increment();
			}
			jobs.forEach(sink);
			this.mailbox.markParsed(alert.emailId(), jobs.size());
		}
	}

}
