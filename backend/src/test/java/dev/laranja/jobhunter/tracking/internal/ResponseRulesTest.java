package dev.laranja.jobhunter.tracking.internal;

import java.time.Duration;
import java.time.Instant;

import dev.laranja.jobhunter.tracking.ApplicationStatus;
import dev.laranja.jobhunter.tracking.EventOrigin;
import dev.laranja.jobhunter.tracking.ResponseChannel;
import dev.laranja.jobhunter.tracking.ResponseType;
import dev.laranja.jobhunter.tracking.TrackingProperties.FollowUp;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

class ResponseRulesTest {

	private static final FollowUp POLICY = new FollowUp(Duration.ofDays(7), Duration.ofDays(7), 2);

	private static final Instant NOW = Instant.parse("2026-10-23T12:00:00Z");

	@ParameterizedTest
	@CsvSource(nullValues = "NULL", textBlock = """
			rh@bancoexemplo.com.br,      bancoexemplo
			talentos@rh.fintech.io,      fintech
			ana@empresa.co.uk,           empresa
			recrutadora@gmail.com,       NULL
			noreply@gupy.io,             NULL
			jobs@greenhouse.io,          NULL
			""")
	void senderDomainIdentifiesTheCompany(String sender, String expected) {
		assertThat(ApplicationMatcher.domainCore(sender)).isEqualTo(expected);
	}

	@Test
	void interviewInviteMovesAnAppliedApplication() {
		Application application = persisted(Application.start(1, ApplicationStatus.APLICADA, 7L, null, null, null, POLICY, NOW));

		ApplicationEvent event = application.recordResponse(ResponseType.CONVITE_ENTREVISTA, ResponseChannel.EMAIL,
				"Convite", ApplicationStatus.ENTREVISTA, EventOrigin.EMAIL, 99L, NOW);

		assertThat(application.getStatus()).isEqualTo(ApplicationStatus.ENTREVISTA);
		assertThat(event.getFromStatus()).isEqualTo(ApplicationStatus.APLICADA);
		assertThat(event.getResponseType()).isEqualTo(ResponseType.CONVITE_ENTREVISTA);
	}

	@Test
	void responseIsOnlyRecordedWhenTheMoveIsNotPossible() {
		Application interest = persisted(Application.start(1, ApplicationStatus.INTERESSE, null, null, null, null, POLICY, NOW));
		ApplicationEvent withoutResume = interest.recordResponse(ResponseType.CONVITE_ENTREVISTA, ResponseChannel.WHATSAPP,
				null, ApplicationStatus.ENTREVISTA, EventOrigin.MANUAL, null, NOW);
		assertThat(interest.getStatus()).isEqualTo(ApplicationStatus.INTERESSE);
		assertThat(withoutResume.getToStatus()).isNull();

		Application rejected = persisted(Application.start(1, ApplicationStatus.RECUSADA, 7L, null, null, null, POLICY, NOW));
		rejected.recordResponse(ResponseType.CONVITE_ENTREVISTA, ResponseChannel.EMAIL, null, ApplicationStatus.ENTREVISTA,
				EventOrigin.EMAIL, 1L, NOW);
		assertThat(rejected.getStatus()).isEqualTo(ApplicationStatus.RECUSADA);
	}

	@Test
	void responseTypesMapToFunnelStages() {
		assertThat(ResponseType.RECUSA.targetStatus()).hasValue(ApplicationStatus.RECUSADA);
		assertThat(ResponseType.PEDIDO_INFORMACAO.targetStatus()).isEmpty();
	}

	private static Application persisted(Application application) {
		ReflectionTestUtils.setField(application, "id", 1L);
		return application;
	}

}
