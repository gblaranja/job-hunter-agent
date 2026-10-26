package dev.laranja.jobhunter.notifications.web;

import java.util.List;

import dev.laranja.jobhunter.notifications.Notifications;
import dev.laranja.jobhunter.notifications.Notifications.NotificationView;
import dev.laranja.jobhunter.notifications.internal.TelegramChannel;
import dev.laranja.jobhunter.notifications.internal.TelegramChannel.TelegramException;
import dev.laranja.jobhunter.notifications.internal.TelegramChannel.TelegramStatus;
import dev.laranja.jobhunter.shared.BusinessRuleException;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/notifications")
class NotificationController {

	private final Notifications notifications;

	private final TelegramChannel telegram;

	NotificationController(Notifications notifications, TelegramChannel telegram) {
		this.notifications = notifications;
		this.telegram = telegram;
	}

	@GetMapping
	List<NotificationView> recent() {
		return this.notifications.recent();
	}

	@PatchMapping("/{id}/read")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void read(@PathVariable long id) {
		this.notifications.markRead(id);
	}

	@PostMapping("/read-all")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void readAll() {
		this.notifications.markAllRead();
	}

	/** Se o Telegram está configurado (o token nunca sai do servidor) e quais tipos vão ao celular. */
	@GetMapping("/telegram")
	TelegramStatus telegram() {
		return this.telegram.status();
	}

	/** Manda uma mensagem de teste ao Telegram; 422 com o motivo quando não está configurado ou o envio falha. */
	@PostMapping("/telegram/test")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void testTelegram() {
		try {
			this.telegram.sendTest();
		}
		catch (TelegramException ex) {
			throw new BusinessRuleException(ex.getMessage());
		}
	}

}
