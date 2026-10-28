package dev.laranja.jobhunter.jobs.internal;

import dev.laranja.jobhunter.jobs.JobOrigin;
import dev.laranja.jobhunter.jobs.internal.JobDuplicates.Match;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class JobDuplicatesMatchTest {

	@Test
	void engagedJobStaysCanonicalEvenAgainstTheAts() {
		Match match = new Match(2, 1, JobOrigin.GREENHOUSE, JobOrigin.LINKEDIN_ALERTA, 3000, 0, true);

		assertThat(match.canonicalId()).isEqualTo(1);
		assertThat(match.duplicateId()).isEqualTo(2);
	}

	@Test
	void atsWinsOverAlertsAndThenTheLongerDescription() {
		assertThat(new Match(2, 1, JobOrigin.GREENHOUSE, JobOrigin.LINKEDIN_ALERTA, 10, 5000, false).canonicalId())
			.isEqualTo(2);
		assertThat(new Match(2, 1, JobOrigin.GUPY_ALERTA, JobOrigin.LEVER, 9000, 10, false).canonicalId()).isEqualTo(1);
		assertThat(new Match(2, 1, JobOrigin.GUPY_ALERTA, JobOrigin.LINKEDIN_ALERTA, 900, 0, false).canonicalId())
			.isEqualTo(2);
		assertThat(new Match(2, 1, JobOrigin.GUPY_ALERTA, JobOrigin.LINKEDIN_ALERTA, 0, 0, false).canonicalId())
			.isEqualTo(1);
	}

}
