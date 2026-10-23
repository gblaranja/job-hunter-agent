package dev.laranja.jobhunter.tracking.internal;

import java.time.Instant;

import dev.laranja.jobhunter.shared.BusinessRuleException;
import dev.laranja.jobhunter.tracking.ApplicationChannel;
import dev.laranja.jobhunter.tracking.ApplicationStatus;
import dev.laranja.jobhunter.tracking.EventOrigin;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ApplicationTest {

	private static final Instant NOW = Instant.parse("2026-10-23T12:00:00Z");

	private static final long RESUME = 7L;

	@Test
	void cannotStartAsNova() {
		assertThatThrownBy(() -> Application.start(1, ApplicationStatus.NOVA, null, null, null, null, NOW))
			.isInstanceOf(BusinessRuleException.class);
	}

	@ParameterizedTest
	@EnumSource(value = ApplicationStatus.class, names = { "APLICADA", "TRIAGEM", "ENTREVISTA", "TESTE_TECNICO",
			"OFERTA", "RECUSADA" })
	void statusesFromAplicadaOnRequireResume(ApplicationStatus status) {
		assertThatThrownBy(() -> Application.start(1, status, null, null, null, null, NOW))
			.isInstanceOf(BusinessRuleException.class)
			.hasMessageContaining("currículo");
	}

	@Test
	void registeringAnOldApplicationKeepsItsDate() {
		Instant appliedAt = Instant.parse("2026-09-01T10:00:00Z");

		Application application = Application.start(1, ApplicationStatus.ENTREVISTA, RESUME,
				ApplicationChannel.SITE_EMPRESA, appliedAt, null, NOW);

		assertThat(application.getAppliedAt()).isEqualTo(appliedAt);
	}

	@Test
	void interestHasNoApplicationDate() {
		Application application = Application.start(1, ApplicationStatus.INTERESSE, null, null, null, null, NOW);

		assertThat(application.getAppliedAt()).isNull();
	}

	@Test
	void applyingRequiresResumeAndRecordsTheMove() {
		Application application = persisted(Application.start(1, ApplicationStatus.INTERESSE, null, null, null, null, NOW));

		assertThatThrownBy(() -> application.moveTo(ApplicationStatus.APLICADA, null, null, EventOrigin.MANUAL, NOW))
			.isInstanceOf(BusinessRuleException.class);

		ApplicationEvent event = application.moveTo(ApplicationStatus.APLICADA, RESUME, null, EventOrigin.MANUAL, NOW);

		assertThat(application.getStatus()).isEqualTo(ApplicationStatus.APLICADA);
		assertThat(application.getResumeVersionId()).isEqualTo(RESUME);
		assertThat(application.getAppliedAt()).isEqualTo(NOW);
		assertThat(event.getFromStatus()).isEqualTo(ApplicationStatus.INTERESSE);
		assertThat(event.getToStatus()).isEqualTo(ApplicationStatus.APLICADA);
	}

	@Test
	void laterStagesReuseTheRegisteredResume() {
		Application application = persisted(
				Application.start(1, ApplicationStatus.APLICADA, RESUME, null, null, null, NOW));

		application.moveTo(ApplicationStatus.ENTREVISTA, null, null, EventOrigin.MANUAL, NOW);

		assertThat(application.getResumeVersionId()).isEqualTo(RESUME);
	}

	@Test
	void givingUpBeforeApplyingNeedsNoResume() {
		Application application = persisted(Application.start(1, ApplicationStatus.INTERESSE, null, null, null, null, NOW));

		application.moveTo(ApplicationStatus.DESISTI, null, null, EventOrigin.MANUAL, NOW);

		assertThat(application.getStatus()).isEqualTo(ApplicationStatus.DESISTI);
	}

	@Test
	void reopeningAClosedApplicationRequiresANote() {
		Application application = persisted(
				Application.start(1, ApplicationStatus.RECUSADA, RESUME, null, null, null, NOW));

		assertThatThrownBy(() -> application.moveTo(ApplicationStatus.ENTREVISTA, null, " ", EventOrigin.MANUAL, NOW))
			.isInstanceOf(BusinessRuleException.class)
			.hasMessageContaining("nota");

		ApplicationEvent event = application.moveTo(ApplicationStatus.ENTREVISTA, null, "Recrutadora reabriu o processo",
				EventOrigin.MANUAL, NOW);

		assertThat(event.getNote()).isEqualTo("Recrutadora reabriu o processo");
	}

	@Test
	void rejectsMovingToTheSameStatusOrBackToNova() {
		Application application = persisted(Application.start(1, ApplicationStatus.INTERESSE, null, null, null, null, NOW));

		assertThatThrownBy(() -> application.moveTo(ApplicationStatus.INTERESSE, null, null, EventOrigin.MANUAL, NOW))
			.isInstanceOf(BusinessRuleException.class);
		assertThatThrownBy(() -> application.moveTo(ApplicationStatus.NOVA, null, null, EventOrigin.MANUAL, NOW))
			.isInstanceOf(BusinessRuleException.class);
	}

	private static Application persisted(Application application) {
		ReflectionTestUtils.setField(application, "id", 42L);
		return application;
	}

}
