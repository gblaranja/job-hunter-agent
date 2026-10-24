package dev.laranja.jobhunter.notifications.internal;

import java.util.List;

import org.jspecify.annotations.Nullable;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Canais externos das notificações.
 *
 * @param publicUrl endereço do backoffice visto do celular; os links das mensagens apontam para ele
 */
@ConfigurationProperties("jobhunter.notifications")
record NotificationProperties(@DefaultValue("http://localhost:5173") String publicUrl,
		@DefaultValue Telegram telegram) {

	/**
	 * Bot do Telegram que manda os alertas ao celular. Sem o token ou o chat id, o canal fica desligado.
	 * @param botToken token do BotFather; nunca vai para o log
	 * @param chatId id da conversa com o bot (o seu usuário)
	 * @param types tipos de notificação enviados ao Telegram; os demais ficam só no sino
	 * @param baseUrl endereço da Bot API (sobrescrito nos testes)
	 */
	record Telegram(@Nullable String botToken, @Nullable String chatId,
			@DefaultValue({ "VAGA_ALVO", "FOLLOW_UP", "PREPARACAO_PRONTA", "RESUMO_DIARIO" }) List<String> types,
			@DefaultValue("https://api.telegram.org") String baseUrl) {

		boolean enabled() {
			return this.botToken != null && !this.botToken.isBlank() && this.chatId != null
					&& !this.chatId.isBlank();
		}

	}

}
