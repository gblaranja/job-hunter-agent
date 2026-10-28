package dev.laranja.jobhunter.jobs.web;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Ordenação da caixa de vagas.
 *
 * @param freshnessBonus pontos somados à aderência de uma vaga recém-vista; cai em linha reta até zero
 * @param freshnessWindow depois desse tempo desde a primeira vez que a vaga foi vista, o bônus de frescor é zero
 * @param mediumLegitimacyPenalty pontos que uma vaga de legitimidade MEDIA perde na ordenação
 */
@ConfigurationProperties("jobhunter.inbox")
record InboxProperties(@DefaultValue("10") int freshnessBonus, @DefaultValue("PT72H") Duration freshnessWindow,
		@DefaultValue("5") int mediumLegitimacyPenalty) {
}
