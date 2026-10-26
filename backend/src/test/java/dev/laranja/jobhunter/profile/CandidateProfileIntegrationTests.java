package dev.laranja.jobhunter.profile;

import dev.laranja.jobhunter.IntegrationTest;
import dev.laranja.jobhunter.TestFiles;
import org.junit.jupiter.api.Test;

import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CandidateProfileIntegrationTests extends IntegrationTest {

	@Test
	void startsWithDefaultsAndKeepsWhatWasSaved() throws Exception {
		this.mvc.perform(get("/api/candidate-profile").with(owner()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.weeklyGoal").value(12))
			.andExpect(jsonPath("$.acceptedContracts[0]").value("CLT"))
			.andExpect(jsonPath("$.workAuthorizations[0]").value("BR"));

		this.mvc.perform(put("/api/candidate-profile").with(owner()).contentType(MediaType.APPLICATION_JSON).content("""
				{"yearsOfExperience": 6, "englishLevel": "AVANCADO", "city": "Ribeirão Preto", "state": "sp",
				 "maxOnsiteDays": 2, "acceptedContracts": ["CLT", "pj", "CLT"], "workAuthorizations": ["br"],
				 "educationLevel": "SUPERIOR", "education": "Ciência da Computação",
				 "certifications": ["AWS Certified Developer"], "desiredSalaryClt": 15000, "desiredSalaryPj": 20000.50,
				 "weeklyGoal": 10}
				"""))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.state").value("SP"))
			.andExpect(jsonPath("$.acceptedContracts.length()").value(2))
			.andExpect(jsonPath("$.acceptedContracts[1]").value("PJ"));

		this.mvc.perform(get("/api/candidate-profile").with(owner()))
			.andExpect(jsonPath("$.yearsOfExperience").value(6))
			.andExpect(jsonPath("$.englishLevel").value("AVANCADO"))
			.andExpect(jsonPath("$.desiredSalaryPj").value(20000.50))
			.andExpect(jsonPath("$.weeklyGoal").value(10))
			.andExpect(jsonPath("$.certifications[0]").value("AWS Certified Developer"));
	}

	@Test
	void rejectsInvalidValues() throws Exception {
		this.mvc.perform(put("/api/candidate-profile").with(owner()).contentType(MediaType.APPLICATION_JSON)
			.content("{\"maxOnsiteDays\": 6, \"acceptedContracts\": [\"ESTAGIO\"]}")).andExpect(status().isBadRequest());
	}

	@Test
	void suggestsYearsAndEnglishFromTheResume() throws Exception {
		uploadResume(TestFiles.pdfWithText("Desenvolvedor Java. Banco X 03/2017 - atual. Ingles avancado."), "cv.pdf",
				"Backend");
		await(() -> this.jdbc.sql("select count(*) from candidate_profile where suggested_from is not null")
			.query(Long.class)
			.single() == 1);

		this.mvc.perform(get("/api/candidate-profile").with(owner()))
			.andExpect(jsonPath("$.suggestedEnglishLevel").value("AVANCADO"))
			.andExpect(jsonPath("$.suggestedFrom").value("Backend"))
			.andExpect(jsonPath("$.yearsOfExperience").doesNotExist());
	}

}
