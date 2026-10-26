package dev.laranja.jobhunter.tracking;

import dev.laranja.jobhunter.IntegrationTest;
import dev.laranja.jobhunter.TestFiles;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** A empresa onde você foi finalista abre uma vaga parecida depois do intervalo: vira notificação. */
class FinalistIntegrationTests extends IntegrationTest {

	@Test
	void aSimilarJobAtACompanyWhereYouWereAFinalistRaisesANotification() throws Exception {
		long applicationId = finalistAt("Banco Exemplo", 120);

		long newJob = createJob("Desenvolvedor Java Sênior II", "Banco Exemplo", "Java");

		await(() -> count("select count(*) from notification where type = 'FINALISTA'") == 1);
		assertThat(this.jdbc.sql("select link from notification where type = 'FINALISTA'").query(String.class).single())
			.isEqualTo("/jobs/" + newJob + "?mensagem=PEDIDO_INDICACAO");
		assertThat(this.jdbc.sql("select title from notification where type = 'FINALISTA'").query(String.class).single())
			.contains("Banco Exemplo");
		assertThat(applicationId).isPositive();
	}

	@Test
	void aRecentRejectionOrADifferentRoleDoesNotNotify() throws Exception {
		finalistAt("Banco Exemplo", 10);
		finalistAt("Fintech Exemplo", 200);

		createJob("Desenvolvedor Java Sênior II", "Banco Exemplo", "Java");
		createJob("Analista de Marketing", "Fintech Exemplo", "Marketing");
		awaitListeners();

		assertThat(count("select count(*) from notification where type = 'FINALISTA'")).isZero();
	}

	/** Candidatura que passou pela entrevista e foi recusada há {@code daysAgo} dias. */
	private long finalistAt(String company, int daysAgo) throws Exception {
		long resume = uploadResume(TestFiles.pdf("finalista-" + company), "cv.pdf", "Backend " + company);
		long jobId = createJob("Desenvolvedor Java Sênior", company, "Java");
		long applicationId = startApplication(jobId,
				"{\"status\": \"APLICADA\", \"resumeVersionId\": %d}".formatted(resume));
		this.jdbc.sql("""
				insert into application_event (application_id, from_status, to_status, origin, created_at)
				values (:id, 'APLICADA', 'ENTREVISTA', 'MANUAL', now() - make_interval(days => :days + 5)),
				       (:id, 'ENTREVISTA', 'RECUSADA', 'MANUAL', now() - make_interval(days => :days))
				""").param("id", applicationId).param("days", daysAgo).update();
		this.jdbc.sql("update application set status = 'RECUSADA' where id = :id").param("id", applicationId).update();
		awaitListeners();
		return applicationId;
	}

	private long count(String sql) {
		return this.jdbc.sql(sql).query(Long.class).single();
	}

}
