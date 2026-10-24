package dev.laranja.jobhunter.recognition;

import java.time.Year;
import java.time.ZoneId;

import com.jayway.jsonpath.JsonPath;
import dev.laranja.jobhunter.IntegrationTest;
import org.junit.jupiter.api.Test;

import org.springframework.http.MediaType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Sem a Claude API configurada: a lista é lida uma empresa por linha. */
class RankingImportIntegrationTests extends IntegrationTest {

	private static final int YEAR = Year.now(ZoneId.of("America/Sao_Paulo")).getValue();

	@Test
	void matchesTheListWithRegisteredCompaniesAndConfirmsTheChosenOnes() throws Exception {
		long alfa = company("Alfa Tecnologia", "alfa tecnologia");
		long beta = company("Beta Pagamentos", "beta pagamentos");
		long gama = company("Gama Sistemas", "gama sistemas");

		String preview = this.mvc.perform(post("/api/recognitions/import").with(owner())
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"text\": \"1. Alfa Tecnologia\\n2. BETA PAGAMENTOS S.A.\\n3. Gamma Sistemas\\n4. Empresa Desconhecida\"}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.matched").value(3))
			.andExpect(jsonPath("$.items[0].match").value("EXATO"))
			.andExpect(jsonPath("$.items[1].companyId").value((int) beta))
			.andExpect(jsonPath("$.items[1].match").value("EXATO"))
			.andExpect(jsonPath("$.items[2].match").value("SIMILAR"))
			.andExpect(jsonPath("$.items[2].companyName").value("Gama Sistemas"))
			.andExpect(jsonPath("$.items[3].match").value("NENHUM"))
			.andReturn()
			.getResponse()
			.getContentAsString();
		assertThat((Integer) JsonPath.read(preview, "$.items[2].position")).isEqualTo(3);

		// O parecido não foi confirmado: só Alfa e Beta ganham o selo.
		this.mvc.perform(post("/api/recognitions/import/confirm").with(owner())
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"level": "RANKING_TI", "year": %d, "rankingName": "GPTW TI %d", "evidenceUrl": "https://gptw.com.br/ranking/ti",
					 "items": [{"companyId": %d, "position": 1}, {"companyId": %d, "position": 2}]}
					""".formatted(YEAR, YEAR, alfa, beta)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.companies").value(2));

		this.mvc.perform(get("/api/companies").param("gptw", "true").with(owner()))
			.andExpect(jsonPath("$[*].name", containsInAnyOrder("Alfa Tecnologia", "Beta Pagamentos")));
		this.mvc.perform(get("/api/companies/{id}/recognitions", alfa).with(owner()))
			.andExpect(jsonPath("$[0].source").value("IMPORTACAO"))
			.andExpect(jsonPath("$[0].verifiedBy").value("USUARIO"))
			.andExpect(jsonPath("$[0].rankingPosition").value(1));
		assertThat(this.jdbc.sql("select bonus from company_gptw where company_id = :id").param("id", alfa)
			.query(Integer.class)
			.single()).isEqualTo(RecognitionLevel.RANKING_TI.bonus());
		assertThat(this.jdbc.sql("select count(*) from company_gptw where company_id = :id").param("id", gama)
			.query(Long.class)
			.single()).isZero();

		// Importar a mesma lista de novo não duplica o histórico.
		this.mvc.perform(post("/api/recognitions/import/confirm").with(owner())
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"level": "RANKING_TI", "year": %d, "rankingName": "GPTW TI %d", "items": [{"companyId": %d}]}
					""".formatted(YEAR, YEAR, alfa)))
			.andExpect(status().isOk());
		assertThat(this.jdbc.sql("select count(*) from company_recognition where company_id = :id").param("id", alfa)
			.query(Long.class)
			.single()).isEqualTo(1);
	}

	@Test
	void withoutTheSearchKeyNothingIsSearched() throws Exception {
		createJob("Dev Java", "Zeta", "Java");
		awaitListeners();

		assertThat(this.jdbc.sql("select count(*) from company_recognition").query(Long.class).single()).isZero();
		this.mvc.perform(get("/api/recognitions/status").with(owner())).andExpect(jsonPath("$.enabled").value(false));
		long zeta = this.jdbc.sql("select id from company where name = 'Zeta'").query(Long.class).single();
		this.mvc.perform(post("/api/companies/{id}/recognitions/check", zeta).with(owner()))
			.andExpect(status().isUnprocessableEntity());
	}

	private long company(String name, String normalized) {
		return this.jdbc.sql("insert into company (name, normalized_name) values (:name, :normalized) returning id")
			.param("name", name)
			.param("normalized", normalized)
			.query(Long.class)
			.single();
	}

}
