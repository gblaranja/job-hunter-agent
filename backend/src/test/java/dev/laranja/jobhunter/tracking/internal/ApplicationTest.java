package dev.laranja.jobhunter.tracking.internal;

import java.time.Duration;
import java.time.Instant;

import dev.laranja.jobhunter.shared.BusinessRuleException;
import dev.laranja.jobhunter.tracking.ApplicationChannel;
import dev.laranja.jobhunter.tracking.ApplicationStatus;
import dev.laranja.jobhunter.tracking.EventOrigin;
import dev.laranja.jobhunter.tracking.NextActionKind;
import dev.laranja.jobhunter.tracking.ResponseChannel;
import dev.laranja.jobhunter.tracking.ResponseType;
import dev.laranja.jobhunter.tracking.TrackingProperties.FollowUp;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ApplicationTest {

	private static final Instant NOW = Instant.parse("2026-10-23T12:00:00Z");

	private static final long RESUME = 7L;

	private static final FollowUp POLICY = new FollowUp(Duration.ofDays(7), Duration.ofDays(7), 2);

	@Test
	void cannotStartAsNova() {
		assertThatThrownBy(() -> Application.start(1, ApplicationStatus.NOVA, null, null, null, null, POLICY, NOW))
			.isInstanceOf(BusinessRuleException.class);
	}

	@ParameterizedTest
	@EnumSource(value = ApplicationStatus.class, names = { "APLICADA", "TRIAGEM", "ENTREVISTA", "TESTE_TECNICO",
			"OFERTA", "RECUSADA" })
	void statusesFromAplicadaOnRequireResume(ApplicationStatus status) {
		assertThatThrownBy(() -> Application.start(1, status, null, null, null, null, POLICY, NOW))
			.isInstanceOf(BusinessRuleException.class)
			.hasMessageContaining("currículo");
	}

	@Test
	void registeringAnOldApplicationKeepsItsDate() {
		Instant appliedAt = Instant.parse("2026-09-01T10:00:00Z");

		Application application = Application.start(1, ApplicationStatus.ENTREVISTA, RESUME,
				ApplicationChannel.SITE_EMPRESA, appliedAt, null, POLICY, NOW);

		assertThat(application.getAppliedAt()).isEqualTo(appliedAt);
	}

	@Test
	void interestHasNoApplicationDate() {
		Application application = Application.start(1, ApplicationStatus.INTERESSE, null, null, null, null, POLICY, NOW);

		assertThat(application.getAppliedAt()).isNull();
	}

	@Test
	void applyingRequiresResumeAndRecordsTheMove() {
		Application application = persisted(Application.start(1, ApplicationStatus.INTERESSE, null, null, null, null, POLICY, NOW));

		assertThatThrownBy(() -> application.moveTo(ApplicationStatus.APLICADA, null, null, EventOrigin.MANUAL, POLICY, NOW))
			.isInstanceOf(BusinessRuleException.class);

		ApplicationEvent event = application.moveTo(ApplicationStatus.APLICADA, RESUME, null, EventOrigin.MANUAL, POLICY, NOW);

		assertThat(application.getStatus()).isEqualTo(ApplicationStatus.APLICADA);
		assertThat(application.getResumeVersionId()).isEqualTo(RESUME);
		assertThat(application.getAppliedAt()).isEqualTo(NOW);
		assertThat(event.getFromStatus()).isEqualTo(ApplicationStatus.INTERESSE);
		assertThat(event.getToStatus()).isEqualTo(ApplicationStatus.APLICADA);
	}

	@Test
	void laterStagesReuseTheRegisteredResume() {
		Application application = persisted(
				Application.start(1, ApplicationStatus.APLICADA, RESUME, null, null, null, POLICY, NOW));

		application.moveTo(ApplicationStatus.ENTREVISTA, null, null, EventOrigin.MANUAL, POLICY, NOW);

		assertThat(application.getResumeVersionId()).isEqualTo(RESUME);
	}

	@Test
	void givingUpBeforeApplyingNeedsNoResume() {
		Application application = persisted(Application.start(1, ApplicationStatus.INTERESSE, null, null, null, null, POLICY, NOW));

		application.moveTo(ApplicationStatus.DESISTI, null, null, EventOrigin.MANUAL, POLICY, NOW);

		assertThat(application.getStatus()).isEqualTo(ApplicationStatus.DESISTI);
	}

	@Test
	void reopeningAClosedApplicationRequiresANote() {
		Application application = persisted(
				Application.start(1, ApplicationStatus.RECUSADA, RESUME, null, null, null, POLICY, NOW));

		assertThatThrownBy(() -> application.moveTo(ApplicationStatus.ENTREVISTA, null, " ", EventOrigin.MANUAL, POLICY, NOW))
			.isInstanceOf(BusinessRuleException.class)
			.hasMessageContaining("nota");

		ApplicationEvent event = application.moveTo(ApplicationStatus.ENTREVISTA, null, "Recrutadora reabriu o processo",
				EventOrigin.MANUAL, POLICY, NOW);

		assertThat(event.getNote()).isEqualTo("Recrutadora reabriu o processo");
	}

	@Test
	void rejectsMovingToTheSameStatusOrBackToNova() {
		Application application = persisted(Application.start(1, ApplicationStatus.INTERESSE, null, null, null, null, POLICY, NOW));

		assertThatThrownBy(() -> application.moveTo(ApplicationStatus.INTERESSE, null, null, EventOrigin.MANUAL, POLICY, NOW))
			.isInstanceOf(BusinessRuleException.class);
		assertThatThrownBy(() -> application.moveTo(ApplicationStatus.NOVA, null, null, EventOrigin.MANUAL, POLICY, NOW))
			.isInstanceOf(BusinessRuleException.class);
	}

	@Test
	void applyingSchedulesTheFirstFollowUpAutomatically() {
		Application application = persisted(Application.start(1, ApplicationStatus.INTERESSE, null, null, null, null,
				POLICY, NOW));
		assertThat(application.getNextActionAt()).isNull();

		application.moveTo(ApplicationStatus.APLICADA, RESUME, null, EventOrigin.MANUAL, POLICY, NOW);

		assertThat(application.getNextActionAt()).isEqualTo(NOW.plus(Duration.ofDays(7)));
		assertThat(application.getNextActionKind()).isEqualTo(NextActionKind.FOLLOW_UP);
		assertThat(application.isNextActionAuto()).isTrue();
	}

	@Test
	void anOldApplicationCountsTheFollowUpFromTheApplicationDate() {
		Instant appliedAt = Instant.parse("2026-10-20T12:00:00Z");

		Application application = Application.start(1, ApplicationStatus.APLICADA, RESUME, null, appliedAt, null,
				POLICY, NOW);

		assertThat(application.getNextActionAt()).isEqualTo(appliedAt.plus(Duration.ofDays(7)));
	}

	@Test
	void anyResponseCancelsTheAutomaticFollowUp() {
		Application application = persisted(applied());

		application.recordResponse(ResponseType.OUTRO, ResponseChannel.EMAIL, null, null, EventOrigin.EMAIL, 9L, NOW);

		assertThat(application.getNextActionAt()).isNull();
		assertThat(application.getNextActionKind()).isNull();
	}

	@Test
	void anInformationRequestBecomesAReplyForTheNextDay() {
		Application application = persisted(applied());

		application.recordResponse(ResponseType.PEDIDO_INFORMACAO, ResponseChannel.EMAIL, null, null, EventOrigin.EMAIL,
				9L, NOW);

		assertThat(application.getNextActionKind()).isEqualTo(NextActionKind.RESPONDER);
		assertThat(application.getNextActionAt()).isEqualTo(NOW.plus(Duration.ofDays(1)));
	}

	@Test
	void aResponseKeepsTheNextActionYouSetYourself() {
		Application application = persisted(applied());
		Instant mine = Instant.parse("2026-10-28T15:00:00Z");
		application.updateDetails(null, mine, NextActionKind.OUTRO, NOW);

		application.recordResponse(ResponseType.CONVITE_ENTREVISTA, ResponseChannel.WHATSAPP, null,
				ApplicationStatus.ENTREVISTA, EventOrigin.MANUAL, null, NOW);

		assertThat(application.getNextActionAt()).isEqualTo(mine);
		assertThat(application.isNextActionAuto()).isFalse();
	}

	@Test
	void rejectionClearsAnyNextAction() {
		Application application = persisted(applied());
		application.updateDetails(null, Instant.parse("2026-10-28T15:00:00Z"), NextActionKind.OUTRO, NOW);

		application.recordResponse(ResponseType.RECUSA, ResponseChannel.EMAIL, null, ApplicationStatus.RECUSADA,
				EventOrigin.EMAIL, 9L, NOW);

		assertThat(application.getStatus()).isEqualTo(ApplicationStatus.RECUSADA);
		assertThat(application.getNextActionAt()).isNull();
	}

	@ParameterizedTest
	@EnumSource(value = ApplicationStatus.class, names = { "RECUSADA", "DESISTI", "ENCERRADA" })
	void closingStagesClearTheNextAction(ApplicationStatus target) {
		Application application = persisted(applied());

		application.moveTo(target, null, null, EventOrigin.MANUAL, POLICY, NOW);

		assertThat(application.getNextActionAt()).isNull();
	}

	@Test
	void leavingAplicadaCancelsOnlyTheAutomaticFollowUp() {
		Application application = persisted(applied());

		application.moveTo(ApplicationStatus.TRIAGEM, null, null, EventOrigin.MANUAL, POLICY, NOW);

		assertThat(application.getNextActionAt()).isNull();
	}

	@Test
	void followUpsRescheduleUntilTheMaximumAndThenStop() {
		Application application = persisted(applied());
		Instant first = NOW.plus(Duration.ofDays(7));

		application.registerFollowUp(POLICY, first);

		assertThat(application.getFollowUpsSent()).isEqualTo(1);
		assertThat(application.getNextActionAt()).isEqualTo(first.plus(Duration.ofDays(7)));
		assertThat(application.getNextActionKind()).isEqualTo(NextActionKind.FOLLOW_UP);

		application.registerFollowUp(POLICY, first.plus(Duration.ofDays(7)));

		assertThat(application.getFollowUpsSent()).isEqualTo(2);
		assertThat(application.getNextActionAt()).isNull();
	}

	@Test
	void editingTheSameDayKeepsTheAutomaticSchedule() {
		Application application = persisted(applied());
		Instant noonSameDay = Instant.parse("2026-10-30T15:00:00Z");

		application.updateDetails("Falar com a Ana", noonSameDay, null, NOW);

		assertThat(application.isNextActionAuto()).isTrue();
		assertThat(application.getNextActionAt()).isEqualTo(NOW.plus(Duration.ofDays(7)));

		application.updateDetails("Falar com a Ana", Instant.parse("2026-11-02T15:00:00Z"), null, NOW);

		assertThat(application.isNextActionAuto()).isFalse();
		assertThat(application.getNextActionKind()).isEqualTo(NextActionKind.FOLLOW_UP);
	}

	@Test
	void closedApplicationsHaveNoFollowUpToRegister() {
		Application application = persisted(Application.start(1, ApplicationStatus.RECUSADA, RESUME, null, null, null,
				POLICY, NOW));

		assertThatThrownBy(() -> application.registerFollowUp(POLICY, NOW)).isInstanceOf(BusinessRuleException.class);
	}

	private static Application applied() {
		return Application.start(1, ApplicationStatus.APLICADA, RESUME, null, null, null, POLICY, NOW);
	}

	private static Application persisted(Application application) {
		ReflectionTestUtils.setField(application, "id", 42L);
		return application;
	}

}
