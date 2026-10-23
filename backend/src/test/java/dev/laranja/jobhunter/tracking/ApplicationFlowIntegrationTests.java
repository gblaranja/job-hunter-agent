package dev.laranja.jobhunter.tracking;

import java.util.List;

import dev.laranja.jobhunter.IntegrationTest;
import dev.laranja.jobhunter.TestFiles;
import org.junit.jupiter.api.Test;

import org.springframework.dao.DataAccessException;
import org.springframework.http.MediaType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ApplicationFlowIntegrationTests extends IntegrationTest {

	@Test
	void applyingFreezesTheJobAndRecordsTheResume() throws Exception {
		long resumeId = uploadResume(TestFiles.pdf("flow"), "cv-backend.pdf", "Backend Java");
		long jobId = createJob("Desenvolvedor Java Sênior", "Banco Exemplo",
				"Requisitos: Java 21, Spring Boot e Apache Kafka para eventos de crédito.");
		long applicationId = startApplication(jobId, """
				{"status": "INTERESSE"}
				""");

		this.mvc
			.perform(patch("/api/applications/{id}/status", applicationId).with(owner())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"status": "APLICADA"}
						"""))
			.andExpect(status().is(422))
			.andExpect(jsonPath("$.detail").value(containsString("currículo")));

		this.mvc
			.perform(patch("/api/applications/{id}/status", applicationId).with(owner())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"status": "APLICADA", "resumeVersionId": %d}
						""".formatted(resumeId)))
			.andExpect(status().isNoContent());

		this.mvc.perform(get("/api/applications/{id}/snapshot", applicationId).with(owner()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("APLICADA"))
			.andExpect(jsonPath("$.resume.id").value(resumeId))
			.andExpect(jsonPath("$.resume.label").value("Backend Java"))
			.andExpect(jsonPath("$.snapshot.title").value("Desenvolvedor Java Sênior"))
			.andExpect(jsonPath("$.snapshot.captureSource").value("MANUAL"))
			.andExpect(jsonPath("$.snapshot.linkStatus").value("NAO_VERIFICADO"))
			.andExpect(jsonPath("$.snapshot.descriptionMd").value(containsString("Apache Kafka")));

		this.mvc.perform(get("/api/applications").param("q", "kafka").with(owner()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$", hasSize(1)))
			.andExpect(jsonPath("$[0].applicationId").value(applicationId))
			.andExpect(jsonPath("$[0].excerpt").value(containsString("Apache Kafka")));
		this.mvc.perform(get("/api/applications").param("q", "golang").with(owner()))
			.andExpect(jsonPath("$", hasSize(0)));

		this.mvc.perform(get("/api/applications/board").with(owner()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.columns[?(@.status == 'APLICADA')].cards[*].applicationId",
					hasItem((int) applicationId)))
			.andExpect(jsonPath("$.columns[?(@.status == 'APLICADA')].cards[*].resumeLabel", hasItem("Backend Java")));

		this.mvc.perform(get("/api/jobs/{id}", jobId).with(owner()))
			.andExpect(jsonPath("$.application.status").value("APLICADA"))
			.andExpect(jsonPath("$.timeline", hasSize(2)))
			.andExpect(jsonPath("$.timeline[1].fromStatus").value("INTERESSE"))
			.andExpect(jsonPath("$.timeline[1].toStatus").value("APLICADA"));

		this.mvc.perform(get("/api/resumes").with(owner())).andExpect(jsonPath("$[0].applications").value(1));
	}

	@Test
	void registersAnOldApplicationDirectlyInTheInterviewStage() throws Exception {
		long resumeId = uploadResume(TestFiles.pdf("old"), "cv.pdf", "Geral");
		long jobId = createJob("Engenheiro de Software", "Fintech Exemplo", "Microsserviços em Java.");

		long applicationId = startApplication(jobId, """
				{"status": "ENTREVISTA", "resumeVersionId": %d, "channel": "INDICACAO",
				 "appliedAt": "2026-09-15T13:00:00Z"}
				""".formatted(resumeId));

		this.mvc.perform(get("/api/applications/{id}/snapshot", applicationId).with(owner()))
			.andExpect(jsonPath("$.appliedAt").value("2026-09-15T13:00:00Z"))
			.andExpect(jsonPath("$.channel").value("INDICACAO"))
			.andExpect(jsonPath("$.snapshot.company").value("Fintech Exemplo"));
		this.mvc.perform(get("/api/resumes").with(owner())).andExpect(jsonPath("$[0].interviewRate").value(1.0));
	}

	@Test
	void duplicatedJobAndApplicationPointToTheExistingOnes() throws Exception {
		long jobId = createJob("Desenvolvedor Backend", "Empresa X", "Java");

		this.mvc
			.perform(post("/api/jobs").with(owner()).contentType(MediaType.APPLICATION_JSON).content("""
					{"title": "desenvolvedor   BACKEND", "company": "empresa x", "location": "remoto"}
					"""))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.existingId").value(jobId));

		long applicationId = startApplication(jobId, """
				{"status": "INTERESSE"}
				""");
		this.mvc
			.perform(post("/api/jobs/{id}/application", jobId).with(owner())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"status": "INTERESSE"}
						"""))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.existingId").value(applicationId));
	}

	@Test
	void savedDescriptionCanBeCorrectedAndHistoryIsAppendOnly() throws Exception {
		long resumeId = uploadResume(TestFiles.pdf("edit"), "cv.pdf", "Geral");
		long jobId = createJob("Dev Java", "Empresa Y", "Descrição cortada do alerta");
		long applicationId = startApplication(jobId, """
				{"status": "APLICADA", "resumeVersionId": %d}
				""".formatted(resumeId));

		this.mvc
			.perform(put("/api/applications/{id}/snapshot", applicationId).with(owner())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"url": "https://empresa-y.example/vagas/42", "title": "Dev Java", "company": "Empresa Y",
						 "descriptionMd": "## Requisitos\\n- Java\\n- Kubernetes"}
						"""))
			.andExpect(status().isNoContent());

		this.mvc.perform(get("/api/applications/{id}/snapshot", applicationId).with(owner()))
			.andExpect(jsonPath("$.snapshot.url").value("https://empresa-y.example/vagas/42"))
			.andExpect(jsonPath("$.snapshot.descriptionMd").value(containsString("Kubernetes")));

		assertThatThrownBy(() -> this.jdbc.sql("update application_event set note = 'editado'").update())
			.isInstanceOf(DataAccessException.class)
			.hasMessageContaining("append-only");
	}

	@Test
	void closedJobLeavesTheInboxAndGoesToTheBoardWithItsHistory() throws Exception {
		long jobId = createJob("IA Developer", "CI&T", "Java e IA");
		long otherJobId = createJob("Dev Kotlin", "Empresa K", "Kotlin");
		long otherApplicationId = startApplication(otherJobId, """
				{"status": "INTERESSE"}
				""");

		long applicationId = startApplication(jobId, """
				{"status": "ENCERRADA", "notes": "Não aceita mais candidaturas."}
				""");
		this.mvc
			.perform(patch("/api/applications/{id}/status", otherApplicationId).with(owner())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"status": "ENCERRADA"}
						"""))
			.andExpect(status().isNoContent());

		this.mvc.perform(get("/api/jobs").param("inbox", "true").with(owner()))
			.andExpect(jsonPath("$.total").value(0));
		this.mvc.perform(get("/api/applications/board").with(owner()))
			.andExpect(jsonPath("$.columns[?(@.status == 'ENCERRADA')].cards[*].applicationId",
					hasItem((int) applicationId)))
			.andExpect(jsonPath("$.columns[?(@.status == 'ENCERRADA')].cards[*].applicationId",
					hasItem((int) otherApplicationId)));
		this.mvc.perform(get("/api/jobs/{id}", jobId).with(owner()))
			.andExpect(jsonPath("$.timeline", hasSize(1)))
			.andExpect(jsonPath("$.timeline[0].toStatus").value("ENCERRADA"))
			.andExpect(jsonPath("$.timeline[0].note").value("Não aceita mais candidaturas."));
		this.mvc.perform(get("/api/jobs/{id}", otherJobId).with(owner()))
			.andExpect(jsonPath("$.timeline[1].fromStatus").value("INTERESSE"))
			.andExpect(jsonPath("$.timeline[1].toStatus").value("ENCERRADA"));
		assertThat(this.jdbc.sql("select count(*) from job where closed_at is not null and id in (:ids)")
			.param("ids", List.of(jobId, otherJobId))
			.query(Long.class)
			.single()).isEqualTo(2);

		this.mvc
			.perform(patch("/api/applications/{id}/status", applicationId).with(owner())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"status": "INTERESSE"}
						"""))
			.andExpect(status().is(422));
	}

	@Test
	void archivedResumeCannotBeChosen() throws Exception {
		long defaultResume = uploadResume(TestFiles.pdf("default"), "cv.pdf", "Padrão");
		long oldResume = uploadResume(TestFiles.pdf("antigo"), "cv-antigo.pdf", "Antigo");
		this.mvc
			.perform(patch("/api/resumes/{id}", oldResume).with(owner())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"archived": true}
						"""))
			.andExpect(status().isOk());
		long jobId = createJob("Dev", "Empresa Z", "Java");

		this.mvc
			.perform(post("/api/jobs/{id}/application", jobId).with(owner())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"status": "APLICADA", "resumeVersionId": %d}
						""".formatted(oldResume)))
			.andExpect(status().is(422))
			.andExpect(jsonPath("$.detail").value(containsString("arquivado")));
		startApplication(jobId, """
				{"status": "APLICADA", "resumeVersionId": %d}
				""".formatted(defaultResume));
	}

}
