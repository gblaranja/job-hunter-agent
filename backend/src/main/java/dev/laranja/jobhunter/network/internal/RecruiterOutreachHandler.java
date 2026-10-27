package dev.laranja.jobhunter.network.internal;

import java.util.Arrays;
import java.util.Locale;
import java.util.stream.Collectors;

import dev.laranja.jobhunter.inbox.EmailClassification;
import dev.laranja.jobhunter.inbox.EmailClassified;
import dev.laranja.jobhunter.inbox.Mailbox;
import dev.laranja.jobhunter.network.ContactBook;
import org.jspecify.annotations.Nullable;

import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/**
 * Abordagens de recrutadores e agências: o e-mail vira o contato (com a conversa recebida) e a oportunidade vira
 * uma vaga na caixa, com a empresa cliente quando o recrutador a cita e, senão, a agência. Nada é respondido.
 */
@Component
class RecruiterOutreachHandler {

	private final ContactBook contacts;

	private final Mailbox mailbox;

	RecruiterOutreachHandler(ContactBook contacts, Mailbox mailbox) {
		this.contacts = contacts;
		this.mailbox = mailbox;
	}

	@ApplicationModuleListener
	void on(EmailClassified event) {
		if (event.classification() != EmailClassification.CONTATO_RECRUTADOR) {
			return;
		}
		this.contacts.registerOutreach(new ContactBook.Outreach(event.emailId(), event.sender(), name(event),
				event.companyGuess(), event.agency(), event.jobGuess(), event.subject(), event.receivedAt()));
		this.mailbox.markProcessed(event.emailId());
	}

	/** Nome de quem escreveu, ou o endereço "maria.silva@agencia.com" como "Maria Silva". */
	static String name(EmailClassified event) {
		if (event.recruiterName() != null && !event.recruiterName().isBlank()) {
			return event.recruiterName().strip();
		}
		return nameFromAddress(event.sender());
	}

	static String nameFromAddress(@Nullable String sender) {
		if (sender == null || sender.isBlank()) {
			return "Recrutador(a)";
		}
		int at = sender.indexOf('@');
		String local = (at > 0) ? sender.substring(0, at) : sender;
		String name = Arrays.stream(local.split("[._\\-+]+"))
			.filter(part -> !part.isBlank() && !part.chars().allMatch(Character::isDigit))
			.map(part -> part.substring(0, 1).toUpperCase(Locale.ROOT) + part.substring(1).toLowerCase(Locale.ROOT))
			.collect(Collectors.joining(" "));
		return name.isBlank() ? sender : name;
	}

}
