package dev.laranja.jobhunter.insights.internal;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TimeToApplyBucketTest {

	private static final Instant SEEN = Instant.parse("2026-10-20T10:00:00Z");

	@Test
	void groupsByHoursBetweenTheJobAppearingAndApplying() {
		assertThat(bucket(Duration.ofHours(23))).isEqualTo("ATE_1_DIA");
		assertThat(bucket(Duration.ofHours(24))).isEqualTo("1_A_3_DIAS");
		assertThat(bucket(Duration.ofHours(71))).isEqualTo("1_A_3_DIAS");
		assertThat(bucket(Duration.ofHours(72))).isEqualTo("4_A_7_DIAS");
		assertThat(bucket(Duration.ofDays(7))).isEqualTo("MAIS_DE_7_DIAS");
	}

	@Test
	void manualJobsAndIncoherentDatesHaveNoTimeToApply() {
		assertThat(InsightAggregator.timeToApplyBucket("MANUAL", SEEN, SEEN.plus(Duration.ofHours(2)))).isEqualTo("SEM_DADO");
		assertThat(InsightAggregator.timeToApplyBucket("GREENHOUSE", SEEN, SEEN.minus(Duration.ofHours(2))))
			.isEqualTo("SEM_DADO");
		assertThat(InsightAggregator.timeToApplyBucket("GREENHOUSE", SEEN, null)).isEqualTo("SEM_DADO");
	}

	private static String bucket(Duration elapsed) {
		return InsightAggregator.timeToApplyBucket("LINKEDIN_ALERTA", SEEN, SEEN.plus(elapsed));
	}

}
