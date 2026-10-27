package dev.laranja.jobhunter.shared;

import org.jspecify.annotations.Nullable;

/**
 * Pedido de notificação vindo de um módulo que não pode depender de {@code notifications} (daria ciclo). O módulo de
 * notificações escuta, grava no sino depois do commit e, se o tipo estiver em
 * {@code jobhunter.notifications.telegram.types}, também envia ao Telegram.
 *
 * @param type tipo da notificação, até 30 caracteres (por exemplo, {@code PREPARACAO_PRONTA})
 * @param link rota do frontend (por exemplo, {@code /jobs/42}); no Telegram vira link absoluto
 * @param dedupKey com a mesma chave, a segunda notificação é ignorada
 */
public record NotificationRequested(String type, String title, @Nullable String body, @Nullable String link,
		@Nullable String dedupKey) {
}
