package dev.laranja.jobhunter.sourcing;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import dev.laranja.jobhunter.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** SmartRecruiters, Workable, Teamtailor e Recruitee, e o ATS detectado pela página de carreira. */
class MoreAtsIntegrationTests extends IntegrationTest {

	@Autowired
	private WireMockServer google;

	@BeforeEach
	void reset() {
		this.google.resetAll();
	}

	@Test
	void collectsTheNewAtsFromTheCareersPageLink() throws Exception {
		stubSmartRecruiters();
		this.google.stubFor(WireMock.get(urlPathEqualTo("/api/v1/widget/accounts/epsilon")).willReturn(okJson("""
				{"name": "Epsilon", "jobs": [
				  {"shortcode": "AB12", "title": "Engenheira de Dados", "url": "https://apply.workable.com/j/AB12",
				   "city": "Recife", "state": "PE", "country": "Brazil", "telecommuting": true,
				   "description": "<p>Spark e <strong>Python</strong>.</p>"}]}
				""")));
		this.google.stubFor(WireMock.get(urlPathEqualTo("/teamtailor/zeta/jobs.json")).willReturn(okJson("""
				{"version": "https://jsonfeed.org/version/1.1", "items": [
				  {"id": "tt-1", "title": "Desenvolvedor Java", "url": "https://zeta.teamtailor.com/jobs/1-dev-java",
				   "content_html": "<p>Spring Boot e Kafka.</p>",
				   "_jobposting": {"@type": "JobPosting", "jobLocationType": "TELECOMMUTE",
				     "jobLocation": {"@type": "Place", "address": {"addressLocality": "Curitiba", "addressCountry": "BR"}}}}]}
				""")));
		this.google.stubFor(WireMock.get(urlPathEqualTo("/recruitee/eta/api/offers/")).willReturn(okJson("""
				{"offers": [{"id": 77, "title": "SRE", "careers_url": "https://eta.recruitee.com/o/sre",
				  "location": "Porto Alegre, RS, Brasil", "remote": false, "hybrid": true, "on_site": false,
				  "description": "<p>Kubernetes.</p>", "requirements": "<ul><li>Terraform</li></ul>"}]}
				""")));

		watch("Delta", "https://jobs.smartrecruiters.com/Delta1");
		watch("Epsilon", "https://apply.workable.com/epsilon/");
		watch("Zeta", "https://zeta.teamtailor.com/jobs");
		watch("Eta", "https://eta.recruitee.com/");
		assertThat(this.jdbc.sql("select ats_type || ':' || ats_slug from company where name = 'Zeta'").query(String.class).single())
			.isEqualTo("TEAMTAILOR:zeta");

		collect();

		assertThat(text("select raw_description from job where external_id = 'sr-1'")).contains("Descrição da vaga")
			.contains("- Java 21")
			.doesNotContain("<li>");
		assertThat(text("select url from job where external_id = 'sr-1'")).isEqualTo("https://jobs.smartrecruiters.com/Delta1/sr-1-dev");
		assertThat(text("select work_model from job where external_id = 'sr-1'")).isEqualTo("REMOTO");
		// O detalhe da sr-2 falhou: a vaga entra sem descrição e com o link montado pelo id.
		assertThat(text("select coalesce(raw_description, '-') from job where external_id = 'sr-2'")).isEqualTo("-");
		assertThat(text("select work_model || ' ' || location from job where external_id = 'AB12'"))
			.isEqualTo("REMOTO Recife, PE, Brazil");
		assertThat(text("select raw_description from job where external_id = 'AB12'")).contains("Spark e Python.");
		assertThat(text("select work_model || ' ' || location from job where external_id = 'tt-1'"))
			.isEqualTo("REMOTO Curitiba, BR");
		assertThat(text("select work_model from job where external_id = '77'")).isEqualTo("HIBRIDO");
		assertThat(text("select raw_description from job where external_id = '77'")).contains("Kubernetes.")
			.contains("- Terraform");

		// Na coleta seguinte, só a vaga ainda sem descrição tem o detalhe buscado; a do Recruitee que sumiu é fechada.
		this.google.stubFor(WireMock.get(urlPathEqualTo("/v1/companies/Delta1/postings/sr-2")).willReturn(okJson("""
				{"postingUrl": "https://jobs.smartrecruiters.com/Delta1/sr-2-qa",
				 "jobAd": {"sections": {"jobDescription": {"title": "Vaga", "text": "<p>Testes automatizados.</p>"}}}}
				""")));
		this.google.stubFor(WireMock.get(urlPathEqualTo("/recruitee/eta/api/offers/")).willReturn(okJson("{\"offers\": []}")));
		collect();

		assertThat(this.google.findAll(getRequestedFor(urlPathEqualTo("/v1/companies/Delta1/postings/sr-1")))).hasSize(1);
		assertThat(text("select raw_description from job where external_id = 'sr-2'")).contains("Testes automatizados.");
		assertThat(text("select (closed_at is not null)::text from job where external_id = '77'")).isEqualTo("true");
	}

	@Test
	void rejectsASlugTheAtsSaysDoesNotExist() throws Exception {
		long id = company("Theta");
		// Lever: lista vazia não prova nada; a página pública do job board responde 404.
		this.google.stubFor(WireMock.get(urlPathEqualTo("/v0/postings/theta")).willReturn(okJson("[]")));
		this.google.stubFor(WireMock.get(urlPathEqualTo("/lever-jobs/theta")).willReturn(aResponse().withStatus(404)));
		this.mvc
			.perform(patch("/api/companies/{id}", id).with(owner()).contentType(MediaType.APPLICATION_JSON).content("""
					{"watchStatus": "ALVO", "careersUrl": "https://jobs.lever.co/theta"}
					"""))
			.andExpect(status().isUnprocessableContent())
			.andExpect(jsonPath("$.detail").value("O slug \"theta\" não existe no LEVER. Confira o link da página de carreira."));

		// Empresa sem vagas, mas com a página no ar: aceita.
		this.google.stubFor(WireMock.get(urlPathEqualTo("/lever-jobs/theta")).willReturn(aResponse().withStatus(200)));
		this.mvc
			.perform(patch("/api/companies/{id}", id).with(owner()).contentType(MediaType.APPLICATION_JSON).content("""
					{"watchStatus": "ALVO", "careersUrl": "https://jobs.lever.co/theta"}
					"""))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.atsType").value("LEVER"))
			.andExpect(jsonPath("$.atsSlug").value("theta"))
			.andExpect(jsonPath("$.careersUrl").value("https://jobs.lever.co/theta"));

		// Sem careersUrl no corpo, a página de carreira gravada fica.
		this.mvc
			.perform(patch("/api/companies/{id}", id).with(owner()).contentType(MediaType.APPLICATION_JSON).content("""
					{"watchStatus": "MONITORADA", "atsType": "LEVER", "atsSlug": "theta"}
					"""))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.careersUrl").value("https://jobs.lever.co/theta"));
	}

	@Test
	void checksTheAtsBeforeSaving() throws Exception {
		// SmartRecruiters devolve lista vazia também para empresa inexistente: não dá para confirmar.
		this.google.stubFor(WireMock.get(urlPathEqualTo("/v1/companies/Vazia/postings"))
			.willReturn(okJson("{\"offset\": 0, \"limit\": 1, \"totalFound\": 0, \"content\": []}")));
		this.mvc
			.perform(post("/api/companies/ats-check").with(owner()).contentType(MediaType.APPLICATION_JSON).content("""
					{"careersUrl": "https://careers.smartrecruiters.com/Vazia"}
					"""))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.atsType").value("SMARTRECRUITERS"))
			.andExpect(jsonPath("$.atsSlug").value("Vazia"))
			.andExpect(jsonPath("$.supported").value(true))
			.andExpect(jsonPath("$.status").value("NAO_CONFIRMADO"));

		this.google.stubFor(WireMock.get(urlPathEqualTo("/v1/boards/iota")).willReturn(okJson("{\"name\": \"Iota\"}")));
		this.mvc
			.perform(post("/api/companies/ats-check").with(owner()).contentType(MediaType.APPLICATION_JSON).content("""
					{"careersUrl": "https://job-boards.greenhouse.io/iota/jobs/123"}
					"""))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("ENCONTRADO"));

		this.mvc
			.perform(post("/api/companies/ats-check").with(owner()).contentType(MediaType.APPLICATION_JSON).content("""
					{"careersUrl": "https://carreiras.exemplo.com.br/vagas"}
					"""))
			.andExpect(status().isUnprocessableContent());

		this.mvc
			.perform(post("/api/companies/ats-check").with(owner()).contentType(MediaType.APPLICATION_JSON).content("""
					{"atsType": "GUPY", "atsSlug": "itau"}
					"""))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.supported").value(false))
			.andExpect(jsonPath("$.status").value("NAO_CONFIRMADO"));
	}

	private void stubSmartRecruiters() {
		String path = "/v1/companies/Delta1/postings";
		// Conferência do slug no cadastro (limit=1) e as duas páginas da listagem.
		this.google.stubFor(WireMock.get(urlPathEqualTo(path)).withQueryParam("limit", equalTo("1"))
			.willReturn(okJson("{\"offset\": 0, \"limit\": 1, \"totalFound\": 2, \"content\": [{\"id\": \"sr-1\", \"name\": \"Dev\"}]}")));
		this.google.stubFor(WireMock.get(urlPathEqualTo(path)).withQueryParam("offset", equalTo("0")).willReturn(okJson("""
				{"offset": 0, "limit": 100, "totalFound": 2, "content": [
				  {"id": "sr-1", "name": "Desenvolvedor Backend",
				   "location": {"city": "São Paulo", "country": "br", "fullLocation": "São Paulo, SP, Brasil", "remote": true}}]}
				""")));
		this.google.stubFor(WireMock.get(urlPathEqualTo(path)).withQueryParam("offset", equalTo("1")).willReturn(okJson("""
				{"offset": 1, "limit": 100, "totalFound": 2, "content": [
				  {"id": "sr-2", "name": "Analista de QA", "location": {"city": "Campinas", "country": "br"}}]}
				""")));
		this.google.stubFor(WireMock.get(urlPathEqualTo(path + "/sr-1")).willReturn(okJson("""
				{"postingUrl": "https://jobs.smartrecruiters.com/Delta1/sr-1-dev",
				 "jobAd": {"sections": {
				   "companyDescription": {"title": "Sobre a empresa", "text": "<p>Fintech.</p>"},
				   "jobDescription": {"title": "Descrição da vaga", "text": "<ul><li>Java 21</li><li>AWS</li></ul>"},
				   "qualifications": {"title": "Requisitos", "text": ""}}}}
				""")));
		this.google.stubFor(WireMock.get(urlPathEqualTo(path + "/sr-2")).willReturn(aResponse().withStatus(500)));
	}

	private long company(String name) throws Exception {
		createJob("Vaga manual em " + name, name, "Java");
		return this.jdbc.sql("select id from company where name = :name").param("name", name).query(Long.class).single();
	}

	private void watch(String name, String careersUrl) throws Exception {
		long id = company(name);
		this.mvc
			.perform(patch("/api/companies/{id}", id).with(owner()).contentType(MediaType.APPLICATION_JSON).content("""
					{"watchStatus": "ALVO", "careersUrl": "%s"}
					""".formatted(careersUrl)))
			.andExpect(status().isOk());
	}

	private void collect() throws Exception {
		this.mvc.perform(post("/api/admin/collect").with(owner())).andExpect(status().isOk());
	}

	private String text(String sql) {
		return this.jdbc.sql(sql).query(String.class).single();
	}

}
