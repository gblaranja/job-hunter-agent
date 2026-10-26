package dev.laranja.jobhunter.sourcing;

import java.nio.charset.StandardCharsets;

import com.github.tomakehurst.wiremock.WireMockServer;
import dev.laranja.jobhunter.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Importar vaga por link, sem a Claude API: JSON-LD da página, conteúdo capturado e as recusas. */
class JobImportIntegrationTests extends IntegrationTest {

	@Autowired
	private WireMockServer google;

	@BeforeEach
	void reset() {
		this.google.resetAll();
	}

	@Test
	void jsonLdPageFillsTheFormAndTheJobIsCreatedWithIt() throws Exception {
		String html = new ClassPathResource("fixtures/import/greenhouse-jsonld.html").getContentAsString(StandardCharsets.UTF_8);
		// redireciona antes, como os ATS costumam fazer
		this.google.stubFor(get(urlPathEqualTo("/vagas/123")).willReturn(aResponse().withStatus(301)
			.withHeader("Location", "/jobs/123")));
		this.google.stubFor(get(urlPathEqualTo("/jobs/123")).willReturn(aResponse().withStatus(200)
			.withHeader("Content-Type", "text/html; charset=utf-8")
			.withBody(html)));
		String url = this.google.baseUrl() + "/vagas/123";

		this.mvc.perform(preview("{\"url\": %s}".formatted(json(url))))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.method").value("JSON_LD"))
			.andExpect(jsonPath("$.title").value("Pessoa Desenvolvedora Backend Sênior"))
			.andExpect(jsonPath("$.company").value("Banco Alfa"))
			.andExpect(jsonPath("$.location").value("Remoto (Brasil)"))
			.andExpect(jsonPath("$.workModel").value("REMOTO"))
			.andExpect(jsonPath("$.salaryRange").value("R$ 15.000–19.000/mês"))
			.andExpect(jsonPath("$.description", containsString("- Kafka")))
			.andExpect(jsonPath("$.existingJobId").doesNotExist());

		createJob("Pessoa Desenvolvedora Backend Sênior", "Banco Alfa", "Java");
		this.mvc.perform(preview("{\"url\": %s}".formatted(json(url))))
			.andExpect(jsonPath("$.existingJobId").isNumber())
			.andExpect(jsonPath("$.warnings", hasItem("Essa vaga já está cadastrada.")));
	}

	@Test
	void capturedContentIsReadWithoutDownloadingEvenFromLinkedIn() throws Exception {
		String jsonLd = "[{\"@type\": \"JobPosting\", \"title\": \"Engenheira de Dados\", \"hiringOrganization\": {\"name\": \"Gama\"}}]";
		this.mvc.perform(preview("{\"url\": \"https://www.linkedin.com/jobs/view/123\", \"jsonLd\": %s}".formatted(json(jsonLd))))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.title").value("Engenheira de Dados"))
			.andExpect(jsonPath("$.warnings", hasItem("A página não informa a descrição; preencha à mão.")));

		this.mvc.perform(preview("{\"url\": \"https://www.linkedin.com/jobs/view/123\", \"text\": \"Vaga de QA em Recife\"}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.method").value("TEXTO"))
			.andExpect(jsonPath("$.description").value("Vaga de QA em Recife"));
	}

	@Test
	void refusesLinkedInDownloadsBadLinksAndPagesWithoutAJob() throws Exception {
		this.mvc.perform(preview("{\"url\": \"https://br.linkedin.com/jobs/view/123\"}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.detail", containsString("LinkedIn")));
		this.mvc.perform(preview("{\"url\": \"file:///etc/passwd\"}")).andExpect(status().isBadRequest());

		this.google.stubFor(get(urlPathEqualTo("/vazia")).willReturn(aResponse().withStatus(200)
			.withHeader("Content-Type", "text/html")
			.withBody("<html><body><p>Página inicial</p></body></html>")));
		this.mvc.perform(preview("{\"url\": %s}".formatted(json(this.google.baseUrl() + "/vazia"))))
			.andExpect(status().isUnprocessableContent());
	}

	private static org.springframework.test.web.servlet.RequestBuilder preview(String body) {
		return post("/api/jobs/import/preview").with(owner()).contentType(MediaType.APPLICATION_JSON).content(body);
	}

}
