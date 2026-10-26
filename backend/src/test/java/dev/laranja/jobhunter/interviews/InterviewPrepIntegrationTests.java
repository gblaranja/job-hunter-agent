package dev.laranja.jobhunter.interviews;

import com.jayway.jsonpath.JsonPath;
import dev.laranja.jobhunter.IntegrationTest;
import dev.laranja.jobhunter.TestFiles;
import org.junit.jupiter.api.Test;

import org.springframework.http.MediaType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class InterviewPrepIntegrationTests extends IntegrationTest {

	@Test
	void movingToInterviewBuildsThePrepWithYourStoriesAndTheDebriefSchedulesTheFollowUp() throws Exception {
		this.mvc.perform(post("/api/stories").with(owner())
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"title": "Eventos no motor de crédito", "situation": "O motor caía.", "action": "Usei Kafka.",
					 "result": "Zero quedas.", "skills": ["Kafka"]}
					"""))
			.andExpect(status().isCreated());
		long jobId = createJob("Desenvolvedor Java", "Banco Alfa",
				"Requisitos: Java, Spring Boot e Kafka. Diferenciais: Kubernetes. Remoto.");
		long resumeId = uploadResume(TestFiles.pdf("java"), "cv.pdf", "Backend");
		long applicationId = startApplication(jobId, """
				{"status": "APLICADA", "resumeVersionId": %d}
				""".formatted(resumeId));
		awaitListeners();

		move(applicationId, "ENTREVISTA");
		await(() -> "PRONTO".equals(prepStatus(applicationId)));

		String kafkaQuestion = "$[0].content.questions[?(@.question =~ /.*Kafka.*/)]";
		this.mvc.perform(get("/api/applications/{id}/interview-prep", applicationId).with(owner()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.length()").value(1))
			.andExpect(jsonPath("$[0].stage").value("ENTREVISTA"))
			.andExpect(jsonPath("$[0].source").value("REGRAS"))
			.andExpect(jsonPath("$[0].jobId").value(jobId))
			.andExpect(jsonPath(kafkaQuestion + ".storyTitle", hasItem("Eventos no motor de crédito")))
			.andExpect(jsonPath("$[0].content.questions[-1].question").value(containsString("Banco Alfa")))
			.andExpect(jsonPath("$[0].content.pointsToClarify", hasItem(containsString("Faixa salarial"))))
			.andExpect(jsonPath("$[0].content.questionsToAsk.length()").value(org.hamcrest.Matchers.greaterThan(2)));
		assertThat(count("select count(*) from notification where type = 'PREPARACAO_PRONTA'")).isEqualTo(1);

		// Voltar a Entrevista não gera outra; pedir de novo refaz a mesma.
		move(applicationId, "TRIAGEM");
		move(applicationId, "ENTREVISTA");
		awaitListeners();
		this.mvc.perform(post("/api/applications/{id}/interview-prep", applicationId).with(owner()))
			.andExpect(status().isAccepted())
			.andExpect(jsonPath("$.status").value("GERANDO"));
		await(() -> "PRONTO".equals(prepStatus(applicationId)));
		assertThat(count("select count(*) from interview_prep")).isEqualTo(1);

		// O registro pós-entrevista agenda o follow-up em 5 dias úteis (sexta → sexta seguinte, 9h de Brasília).
		this.mvc.perform(post("/api/applications/{id}/debriefs", applicationId).with(owner())
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"stage": "ENTREVISTA", "interviewedOn": "2026-10-23",
					 "questions": ["Como você usa Kafka?", " ", "Fale de um conflito no time."], "rating": 4,
					 "toImprove": "Faltou falar de observabilidade."}
					"""))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.debrief.questions.length()").value(2))
			.andExpect(jsonPath("$.followUpAt").value("2026-10-30T12:00:00Z"));
		assertThat(this.jdbc.sql("select next_action_kind from application where id = :id")
			.param("id", applicationId)
			.query(String.class)
			.single()).isEqualTo("FOLLOW_UP");

		// A próxima preparação lê as perguntas da etapa anterior.
		move(applicationId, "TESTE_TECNICO");
		await(() -> count("select count(*) from interview_prep where stage = 'TESTE_TECNICO' and status = 'PRONTO'") == 1);
		this.mvc.perform(get("/api/applications/{id}/interview-prep", applicationId).with(owner()))
			.andExpect(jsonPath("$[0].stage").value("TESTE_TECNICO"))
			.andExpect(jsonPath("$[0].content.questions[*].question", hasItem("Como você usa Kafka?")));

		// Uma pergunta repetida em dois registros aparece como recorrente.
		this.mvc.perform(post("/api/applications/{id}/debriefs", applicationId).with(owner())
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"stage": "TESTE_TECNICO", "interviewedOn": "2026-10-27", "questions": ["como você usa kafka"]}
					"""))
			.andExpect(status().isCreated());
		this.mvc.perform(get("/api/interviews/recurring-questions").with(owner()))
			.andExpect(jsonPath("$[0].question").value("como você usa kafka"))
			.andExpect(jsonPath("$[0].count").value(2));
		this.mvc.perform(get("/api/applications/{id}/debriefs", applicationId).with(owner()))
			.andExpect(jsonPath("$.length()").value(2))
			.andExpect(jsonPath("$[1].toImprove").value("Faltou falar de observabilidade."));
	}

	@Test
	void debriefNeedsAnInterviewStage() throws Exception {
		long jobId = createJob("Desenvolvedor Java", "Banco Alfa", "Java.");
		long applicationId = startApplication(jobId, """
				{"status": "INTERESSE"}
				""");
		this.mvc.perform(post("/api/applications/{id}/debriefs", applicationId).with(owner())
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"stage": "OFERTA", "interviewedOn": "2026-10-23"}
					"""))
			.andExpect(status().isUnprocessableContent());
	}

	private void move(long applicationId, String status) throws Exception {
		this.mvc.perform(patch("/api/applications/{id}/status", applicationId).with(owner())
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"status\": \"" + status + "\"}"))
			.andExpect(status().isNoContent());
	}

	private String prepStatus(long applicationId) {
		return this.jdbc.sql("select status from interview_prep where application_id = :id order by updated_at desc limit 1")
			.param("id", applicationId)
			.query(String.class)
			.optional()
			.orElse(null);
	}

	private long count(String sql) {
		return this.jdbc.sql(sql).query(Long.class).single();
	}

	static long id(String json) {
		return ((Number) JsonPath.read(json, "$.id")).longValue();
	}

}
