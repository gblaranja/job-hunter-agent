package dev.laranja.jobhunter.profile;

import java.util.List;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class JobFilterTest {

	private final JobFilter filter = new JobFilter(List.of("PHP", "estágio"), false);

	@Test
	void excludesWholeWordsIgnoringAccentsAndCase() {
		assertThat(this.filter.rejectionReason("Estagio em Java", null, null, "REMOTO")).hasValue("contém \"estágio\"");
		assertThat(this.filter.rejectionReason("Dev Full Stack", "Laravel e PHP 8", null, "REMOTO")).isPresent();
		assertThat(this.filter.rejectionReason("Desenvolvedor Java Sênior", "Spring e Kafka", null, "REMOTO")).isEmpty();
	}

	@Test
	void doesNotMatchInsideOtherWords() {
		assertThat(new JobFilter(List.of("Go"), false).rejectionReason("Google Cloud Engineer", null, null, "REMOTO"))
			.isEmpty();
	}

	@Test
	void remoteOnlyDropsOnSiteAndHybridButKeepsUnknown() {
		JobFilter remote = new JobFilter(List.of(), true);

		assertThat(remote.rejectionReason("Dev", null, null, "PRESENCIAL")).hasValue("não é remota");
		assertThat(remote.rejectionReason("Dev", null, null, "HIBRIDO")).isPresent();
		assertThat(remote.rejectionReason("Dev", null, null, "NAO_INFORMADO")).isEmpty();
	}

}
