package dev.laranja.jobhunter.network;

import java.io.InputStream;

import dev.laranja.jobhunter.IntegrationTest;
import org.junit.jupiter.api.Test;

import org.springframework.mock.web.MockMultipartFile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Conexões do LinkedIn: importadas sem duplicar, casadas com as empresas e contadas na caixa de vagas. */
class ConnectionsImportIntegrationTests extends IntegrationTest {

	@Test
	void importsConnectionsMatchesCompaniesAndShowsThemInTheInbox() throws Exception {
		long bankJob = createJob("Desenvolvedor Java", "Banco Exemplo", "Java");
		createJob("Desenvolvedor Go", "Outra Empresa", "Go");

		upload().andExpect(status().isOk())
			.andExpect(jsonPath("$.created").value(3))
			.andExpect(jsonPath("$.updated").value(0))
			.andExpect(jsonPath("$.matchedCompanies").value(1));
		upload().andExpect(jsonPath("$.created").value(0)).andExpect(jsonPath("$.updated").value(3));

		assertThat(this.jdbc.sql("select count(*) from contact where role = 'CONEXAO' and source = 'LINKEDIN_EXPORT'")
			.query(Long.class).single()).isEqualTo(3);
		assertThat(this.jdbc.sql("select count(*) from company").query(Long.class).single()).isEqualTo(2);

		this.mvc.perform(get("/api/jobs").param("inbox", "true").param("hasConnection", "true").with(owner()))
			.andExpect(jsonPath("$.total").value(1))
			.andExpect(jsonPath("$.items[0].id").value(bankJob))
			.andExpect(jsonPath("$.items[0].connections").value(1));
		this.mvc.perform(get("/api/jobs/{id}/contacts", bankJob).with(owner()))
			.andExpect(jsonPath("$", hasSize(1)))
			.andExpect(jsonPath("$[0].name").value("Ana Lima"))
			.andExpect(jsonPath("$[0].position").value("Engineering Manager"))
			.andExpect(jsonPath("$[0].connectedOn").value("2024-03-15"));
	}

	private org.springframework.test.web.servlet.ResultActions upload() throws Exception {
		byte[] content;
		try (InputStream input = getClass().getResourceAsStream("/fixtures/linkedin/Connections.csv")) {
			content = input.readAllBytes();
		}
		return this.mvc.perform(multipart("/api/contacts/import/linkedin")
			.file(new MockMultipartFile("file", "Connections.csv", "text/csv", content))
			.with(owner()));
	}

}
