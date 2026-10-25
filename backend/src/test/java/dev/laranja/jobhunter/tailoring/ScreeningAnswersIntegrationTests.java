package dev.laranja.jobhunter.tailoring;

import dev.laranja.jobhunter.IntegrationTest;
import dev.laranja.jobhunter.TestFiles;
import org.junit.jupiter.api.Test;

import org.springframework.http.MediaType;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Sem a Claude API: o que os dados da triagem respondem sai pronto; o resto fica para a pessoa. */
class ScreeningAnswersIntegrationTests extends IntegrationTest {

	@Test
	void answersFromTheProfileAndLeavesTheRestToThePerson() throws Exception {
		this.mvc.perform(put("/api/candidate-profile").with(owner()).contentType(MediaType.APPLICATION_JSON)
			.content("{\"yearsOfExperience\": 7, \"englishLevel\": \"AVANCADO\", \"desiredSalaryClt\": 15000}"))
			.andExpect(status().isOk());
		uploadResume(TestFiles.pdfWithText("Desenvolvedor Java Senior Spring Boot Kafka"), "cv.pdf", "Backend");
		long jobId = createJob("Desenvolvedor Java Sênior", "Banco", "Requisitos: Java e Kafka. Contratação CLT.");
		awaitListeners();

		this.mvc.perform(post("/api/jobs/{id}/screening-answers", jobId).with(owner())
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"questions": ["Qual a sua pretensão salarial?", "Quantos anos de experiência você tem com Java?",
					               "Por que quer trabalhar aqui?"], "coverLetter": true}
					"""))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.source").value("REGRAS"))
			.andExpect(jsonPath("$.answers[0].answer").value(containsString("15.000")))
			.andExpect(jsonPath("$.answers[0].needsInput").value(false))
			.andExpect(jsonPath("$.answers[1].answer").value("7 anos"))
			.andExpect(jsonPath("$.answers[2].needsInput").value(true))
			.andExpect(jsonPath("$.coverLetter").value(containsString("Desenvolvedor Java Sênior na Banco")));

		this.mvc.perform(post("/api/jobs/{id}/screening-answers", jobId).with(owner())
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"questions\": [], \"coverLetter\": false}"))
			.andExpect(status().isUnprocessableEntity());
	}

}
