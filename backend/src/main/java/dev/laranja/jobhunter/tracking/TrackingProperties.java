package dev.laranja.jobhunter.tracking;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Candidaturas.
 *
 * @param followUp quando pedir follow-up de uma candidatura sem retorno
 * @param finalistCooldown intervalo depois de uma recusa como finalista para avisar de uma vaga parecida na empresa
 */
@ConfigurationProperties("jobhunter.tracking")
public record TrackingProperties(@DefaultValue FollowUp followUp, @DefaultValue("P90D") Duration finalistCooldown) {

	/**
	 * @param first espera entre aplicar e o primeiro follow-up
	 * @param interval espera entre um follow-up enviado e o próximo
	 * @param max follow-ups sem retorno antes de parar de pedir
	 */
	public record FollowUp(@DefaultValue("P7D") Duration first, @DefaultValue("P7D") Duration interval,
			@DefaultValue("2") int max) {
	}

}
