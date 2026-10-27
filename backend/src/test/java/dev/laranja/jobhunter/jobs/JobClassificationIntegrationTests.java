package dev.laranja.jobhunter.jobs;

import com.jayway.jsonpath.JsonPath;
import dev.laranja.jobhunter.IntegrationTest;
import org.junit.jupiter.api.Test;

import org.springframework.http.MediaType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class JobClassificationIntegrationTests extends IntegrationTest {

	private static final String PORTUGUESE = """
			Buscamos uma pessoa para o nosso time. Você vai trabalhar com as áreas de produto e dados da empresa,
			com autonomia e em contato com os clientes.
			""";

	private static final String US_ONLY = """
			We are looking for a Senior Backend Engineer to join our team. You will build services in Java. This is a
			contractor role and candidates must be authorized to work in the United States, working EST hours.
			""";

	private static final String LATAM = """
			We are looking for a Senior Backend Engineer to join our team. You will build services in Java and work
			with the product team. We hire across Latin America and pay in USD.
			""";

	@Test
	void classifiesFamilyAndInternationalEligibility() throws Exception {
		long backend = createJob("Desenvolvedor Java Sênior", "Banco A", PORTUGUESE);
		long frontend = createJob("Desenvolvedora Front-end React", "Loja B", PORTUGUESE);
		long usOnly = createJob("Senior Backend Engineer", "Acme Inc", US_ONLY);
		long latam = createJob("Senior Backend Engineer", "Globex", LATAM);

		this.mvc.perform(get("/api/jobs").param("inbox", "true").param("family", "FRONTEND").with(owner()))
			.andExpect(jsonPath("$.total").value(1))
			.andExpect(jsonPath("$.items[0].id").value(frontend))
			.andExpect(jsonPath("$.items[0].jobFamily").value("FRONTEND"));

		// a aba Internacionais esconde as vagas restritas a outro país
		this.mvc
			.perform(get("/api/jobs").param("inbox", "true")
				.param("scope", "INTERNACIONAL")
				.param("eligibleFromBrazil", "true")
				.with(owner()))
			.andExpect(jsonPath("$.total").value(1))
			.andExpect(jsonPath("$.items[0].id").value(latam))
			.andExpect(jsonPath("$.items[0].internationalEligibility").value("LATAM"));
		this.mvc.perform(get("/api/jobs").param("inbox", "true").param("scope", "INTERNACIONAL").with(owner()))
			.andExpect(jsonPath("$.total").value(2));

		this.mvc.perform(get("/api/jobs/{id}", usOnly).with(owner()))
			.andExpect(jsonPath("$.classification.jobFamily").value("BACKEND"))
			.andExpect(jsonPath("$.classification.internationalEligibility").value("PAIS_ESPECIFICO"))
			.andExpect(jsonPath("$.classification.eligibleFromBrazil").value(false))
			.andExpect(jsonPath("$.classification.requiredTimezone").value("EST"))
			.andExpect(jsonPath("$.classification.contractor").value(true));
		this.mvc.perform(get("/api/jobs/{id}", backend).with(owner()))
			.andExpect(jsonPath("$.classification.jobFamily").value("BACKEND"));
	}

	@Test
	void choosingFamiliesInTheSearchProfileDismissesOtherFamiliesFromTheInbox() throws Exception {
		long backend = createJob("Desenvolvedor Java Sênior", "Banco A", PORTUGUESE);
		long frontend = createJob("Desenvolvedora Front-end React", "Loja B", PORTUGUESE);
		long sales = createJob("Vendedor Interno", "Loja C", PORTUGUESE);
		long unknown = createJob("Oportunidade", "Loja D", PORTUGUESE);

		String profiles = this.mvc.perform(get("/api/search-profiles").with(owner()))
			.andReturn()
			.getResponse()
			.getContentAsString();
		long profileId = ((Number) JsonPath.read(profiles, "$[0].id")).longValue();
		String name = JsonPath.read(profiles, "$[0].name");
		try {
			updateProfile(profileId, name, "[\"BACKEND\", \"FULLSTACK\"]")
				.andExpect(jsonPath("$.jobFamilies[0]").value("BACKEND"));
			awaitListeners();

			// a vaga sem família reconhecida fica na caixa
			this.mvc.perform(get("/api/jobs").param("inbox", "true").with(owner()))
				.andExpect(jsonPath("$.total").value(2))
				.andExpect(jsonPath("$.items[*].id", containsInAnyOrder((int) backend, (int) unknown)));
			assertThat(this.jdbc.sql("select job_id from job_dismissal where reason = 'SEM_PERFIL_TECNICO' order by job_id")
				.query(Long.class)
				.list()).containsExactly(frontend, sales);
			this.mvc.perform(get("/api/jobs/{id}", frontend).with(owner()))
				.andExpect(jsonPath("$.dismissal.reason").value("SEM_PERFIL_TECNICO"))
				.andExpect(jsonPath("$.dismissal.detail")
					.value("Família de cargo fora dos perfis de busca: Frontend"));
		}
		finally {
			updateProfile(profileId, name, "[]");
			awaitListeners();
		}
	}

	private org.springframework.test.web.servlet.ResultActions updateProfile(long id, String name, String families)
			throws Exception {
		return this.mvc
			.perform(put("/api/search-profiles/{id}", id).with(owner()).contentType(MediaType.APPLICATION_JSON).content("""
					{"name": %s, "keywords": ["Java", "Spring Boot", "Sênior"],
					 "excludedKeywords": ["PHP", "estágio", "estagiário", "júnior"], "locations": ["Remoto", "Ribeirão Preto"],
					 "remoteOnly": false, "active": true, "jobFamilies": %s}
					""".formatted(json(name), families)))
			.andExpect(status().isOk());
	}

}
