package dev.laranja.jobhunter.notifications.web;

import java.util.List;

import dev.laranja.jobhunter.notifications.Notifications;
import dev.laranja.jobhunter.notifications.Notifications.NotificationView;

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

	NotificationController(Notifications notifications) {
		this.notifications = notifications;
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

}
