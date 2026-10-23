package dev.laranja.jobhunter.jobs.internal;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class JobFingerprintTest {

	@Test
	void sameJobWrittenDifferentlyHasSameFingerprint() {
		String fromLinkedIn = JobFingerprint.of("Banco Exemplo", "Desenvolvedor Java Sênior", "São Paulo");
		String fromGupy = JobFingerprint.of("  BANCO EXEMPLO ", "Desenvolvedor   JAVA senior", "sao paulo");

		assertThat(fromLinkedIn).isEqualTo(fromGupy).hasSize(64);
	}

	@Test
	void differentLocationIsAnotherJob() {
		assertThat(JobFingerprint.of("Banco Exemplo", "Desenvolvedor Java", "São Paulo"))
			.isNotEqualTo(JobFingerprint.of("Banco Exemplo", "Desenvolvedor Java", "Remoto"));
	}

	@Test
	void missingLocationIsAccepted() {
		assertThat(JobFingerprint.of("Banco Exemplo", "Desenvolvedor Java", null))
			.isEqualTo(JobFingerprint.of("Banco Exemplo", "Desenvolvedor Java", ""));
	}

}
