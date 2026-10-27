package dev.laranja.jobhunter.notifications.internal;

import dev.laranja.jobhunter.notifications.NotificationCreated;

import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/**
 * Envia ao Telegram as notificações dos tipos configurados, depois do commit. Se o Telegram não responder, o evento
 * fica pendente e o Modulith o reenvia na próxima subida.
 */
@Component
class TelegramNotifier {

	private final TelegramChannel telegram;

	TelegramNotifier(TelegramChannel telegram) {
		this.telegram = telegram;
	}

	@ApplicationModuleListener
	void on(NotificationCreated event) {
		if (this.telegram.sendsType(event.type())) {
			this.telegram.send(event.title(), event.body(), event.link());
		}
	}

}
