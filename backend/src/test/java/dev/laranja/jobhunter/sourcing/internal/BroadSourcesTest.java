package dev.laranja.jobhunter.sourcing.internal;

import java.util.List;

import dev.laranja.jobhunter.jobs.RawJob;
import dev.laranja.jobhunter.jobs.WorkModel;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BroadSourcesTest {

	@Test
	void githubIssueTitleSplitsTagsRoleAndCompany() {
		RawJob job = BroadSources.fromIssue(new BroadSources.GitHubIssue(1, "[Remoto] [Pleno] Desenvolvedor Java na Acme Pagamentos",
				"https://github.com/backend-br/vagas/issues/1", "corpo", List.of(), null));

		assertThat(job.title()).isEqualTo("Desenvolvedor Java");
		assertThat(job.company()).isEqualTo("Acme Pagamentos");
		assertThat(job.location()).isEqualTo("Remoto, Pleno");
		assertThat(job.workModel()).isEqualTo(WorkModel.REMOTO);
	}

	@Test
	void githubIssueWithoutCompanyKeepsTheTitle() {
		RawJob job = BroadSources.fromIssue(new BroadSources.GitHubIssue(2, "Back-end Kotlin Sênior",
				"https://github.com/backend-br/vagas/issues/2", null,
				List.of(new BroadSources.GitHubLabel("Híbrido")), null));

		assertThat(job.title()).isEqualTo("Back-end Kotlin Sênior");
		assertThat(job.company()).isEqualTo("Não informada");
		assertThat(job.workModel()).isEqualTo(WorkModel.HIBRIDO);
	}

	@Test
	void htmlBecomesReadableText() {
		assertThat(HtmlText.toText("&lt;h2&gt;Requisitos&lt;/h2&gt;&lt;ul&gt;&lt;li&gt;Java&amp;nbsp;21&lt;/li&gt;&lt;li&gt;Kafka&lt;/li&gt;&lt;/ul&gt;", true))
			.isEqualTo("Requisitos\n- Java 21\n- Kafka");
		assertThat(AtsSources.workModel("on-site")).isEqualTo(WorkModel.PRESENCIAL);
		assertThat(AtsSources.workModel("Remote")).isEqualTo(WorkModel.REMOTO);
	}

}
