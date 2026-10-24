package dev.laranja.jobhunter.niches;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.jayway.jsonpath.JsonPath;
import dev.laranja.jobhunter.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;

import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class NichesIntegrationTests extends IntegrationTest {

	@Autowired
	private WireMockServer googleApi;

	@Autowired
	private CompanySeeds seeds;

	@BeforeEach
	void stubCentralBank() throws IOException {
		this.googleApi.resetAll();
		for (String set : new String[] { "SedesBancoComMultCE", "SedesSociedades", "SedesCooperativas", "SedesConsorcios" }) {
			this.googleApi.stubFor(WireMock.get(urlPathEqualTo("/" + set))
				.withQueryParam("$format", equalTo("json"))
				.willReturn(okJson(new ClassPathResource("fixtures/bcb/" + set + ".json").getContentAsString(StandardCharsets.UTF_8))));
		}
	}

	@Test
	void centralBankImportFillsTheFinancialNicheBySegmentWithoutSearches() throws Exception {
		// A empresa já apareceu numa vaga com outro nome: a importação casa pelo nome e não duplica.
		long jobId = createJob("Desenvolvedor Backend Java", "Banco Alfa",
				"Java, Spring Boot e Kafka no motor de crédito do banco. Remoto.");
		long alfa = companyOf(jobId);

		this.mvc.perform(post("/api/admin/seeds/bcb/run").with(owner())).andExpect(status().isAccepted());
		await(() -> count("select count(*) from notification where type = 'IMPORTACAO_CONCLUIDA'") == 1);

		assertThat(this.googleApi.getAllServeEvents()).hasSize(4);
		// a cooperativa singular fica de fora; a central entra
		assertThat(count("select count(*) from company where cnpj is not null")).isEqualTo(7);
		assertThat(this.jdbc.sql("select cnpj from company where id = :id").param("id", alfa).query(String.class).single())
			.isEqualTo("11111111");
		assertThat(this.jdbc.sql("select name from company where cnpj = '04444444'").query(String.class).single())
			.isEqualTo("Delta Pay Instituição de Pagamento Ltda");
		assertThat(segmentOf("33333333")).isEqualTo("Crédito");
		assertThat(segmentOf("04444444")).isEqualTo("Pagamentos e adquirência");
		assertThat(segmentOf("66666666")).isEqualTo("Cooperativas de crédito");
		assertThat(segmentOf("88888888")).isEqualTo("Seguros e consórcios");
		// sem vaga, as importadas continuam descobertas; a Alfa publicou vaga e passou a ser monitorada
		assertThat(this.jdbc.sql("select watch_status from company where cnpj = '33333333'").query(String.class).single())
			.isEqualTo("DESCOBERTA");
		assertThat(this.jdbc.sql("select watch_status from company where id = :id").param("id", alfa).query(String.class).single())
			.isEqualTo("MONITORADA");
		// importação não dispara busca de selo
		assertThat(count("select count(*) from company_recognition")).isZero();

		long financial = nicheId("Financeiro");
		String page = this.mvc.perform(get("/api/niches/{id}/companies", financial).with(owner()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.total").value(7))
			.andExpect(jsonPath("$.items[0].name").value("Banco Alfa"))
			.andExpect(jsonPath("$.items[0].source").value("BCB"))
			.andExpect(jsonPath("$.items[0].fitBand").value("ALTO"))
			.andExpect(jsonPath("$.items[0].reasons", hasItem("1 das 1 últimas vagas pedem Java/Spring")))
			.andExpect(jsonPath("$.items[0].trend", hasSize(12)))
			.andReturn()
			.getResponse()
			.getContentAsString();
		assertThat((Integer) JsonPath.read(page, "$.items[0].trend[11]")).isEqualTo(1);
		this.mvc.perform(get("/api/niches/{id}/companies", financial).param("hasOpenJobs", "true").with(owner()))
			.andExpect(jsonPath("$.items[*].name", contains("Banco Alfa")));
		this.mvc.perform(get("/api/niches").with(owner()))
			.andExpect(jsonPath("$[0].name").value("Financeiro"))
			.andExpect(jsonPath("$[0].companies").value(7))
			.andExpect(jsonPath("$[0].segments[?(@.name == 'Crédito')].companies", contains(1)));

		// Rodar de novo não duplica nada.
		this.seeds.run("bcb");
		assertThat(count("select count(*) from company where cnpj is not null")).isEqualTo(7);
	}

	@Test
	void keywordsClassifyCompaniesFromJobsAndYourChoicePrevails() throws Exception {
		long jobId = createJob("Engenheira de Software Java", "Credix Crédito Digital",
				"Plataforma de empréstimos e financiamento para PMEs. Java e Spring.");
		long credix = companyOf(jobId);
		await(() -> count("select count(*) from company_fit where company_id = " + credix) == 1);

		this.mvc.perform(get("/api/companies/{id}/niches", credix).with(owner()))
			.andExpect(jsonPath("$[0].nicheName").value("Financeiro"))
			.andExpect(jsonPath("$[0].segmentName").value("Crédito"))
			.andExpect(jsonPath("$[0].source").value("VAGA"))
			.andExpect(jsonPath("$[0].verified").value(false));

		long financial = nicheId("Financeiro");
		long antifraud = this.jdbc.sql("select id from niche_segment where name = 'Antifraude, risco e bureaus de crédito'")
			.query(Long.class)
			.single();
		this.mvc.perform(put("/api/companies/{id}/niche", credix).with(owner())
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"nicheId\": %d, \"segmentId\": %d}".formatted(financial, antifraud)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[0].segmentName").value("Antifraude, risco e bureaus de crédito"))
			.andExpect(jsonPath("$[0].source").value("MANUAL"));
		this.mvc.perform(post("/api/niches/recompute").with(owner())).andExpect(status().isOk());
		this.mvc.perform(get("/api/companies/{id}/niches", credix).with(owner()))
			.andExpect(jsonPath("$[0].source").value("MANUAL"));

		this.mvc.perform(delete("/api/companies/{id}/niches/{nicheId}", credix, financial).with(owner()))
			.andExpect(jsonPath("$[0].source").value("VAGA"));
	}

	@Test
	void consultanciesThatServeBanksGetTheFinancialClientBonus() throws Exception {
		long jobId = createJob("Desenvolvedor Java Pleno", "Omega Consultoria",
				"Alocação em cliente do setor bancário, contratação PJ, outsourcing. Java e Spring Boot.");
		long omega = companyOf(jobId);
		awaitListeners();
		this.jdbc.sql("update company set kind = 'CONSULTORIA_TI', client_sectors = '{Bancos}' where id = :id")
			.param("id", omega)
			.update();
		this.mvc.perform(post("/api/niches/recompute").with(owner())).andExpect(status().isOk());

		long consultancies = nicheId("Consultorias");
		this.mvc.perform(get("/api/niches/{id}/companies", consultancies).param("financialClients", "true").with(owner()))
			.andExpect(jsonPath("$.items[0].name").value("Omega Consultoria"))
			.andExpect(jsonPath("$.items[0].segmentName").value("Alocação e outsourcing"))
			.andExpect(jsonPath("$.items[0].reasons", hasItem("Aloca em clientes do setor financeiro")));
		// virou consultoria: sai do nicho Financeiro, que só aceita contratantes
		assertThat(count("select count(*) from company_niche where company_id = " + omega + " and niche_id = "
				+ nicheId("Financeiro"))).isZero();
	}

	@Test
	void editingTheWeightsRecomputesTheFit() throws Exception {
		this.seeds.run("bcb");
		long financial = nicheId("Financeiro");
		int before = fitOf("33333333");
		try {
			this.mvc.perform(patch("/api/niches/{id}", financial).with(owner())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"fitWeights\": {\"segment\": 1, \"stack\": 0, \"domain\": 0, \"signals\": 0}}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.fitWeights.segment").value(1.0));
			assertThat(fitOf("33333333")).isEqualTo(90).isNotEqualTo(before);
		}
		finally {
			this.mvc.perform(patch("/api/niches/{id}", financial).with(owner())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"fitWeights\": {\"segment\": 0.35, \"stack\": 0.30, \"domain\": 0.20, \"signals\": 0.15}}"));
		}
	}

	@Test
	void theCnpjImportNeedsTheDirectory() throws Exception {
		this.mvc.perform(get("/api/admin/seeds").with(owner()))
			.andExpect(jsonPath("$[?(@.key == 'cnpj')].available", contains(false)));
		this.mvc.perform(post("/api/admin/seeds/cnpj/run").with(owner())).andExpect(status().isUnprocessableEntity());
	}

	private long companyOf(long jobId) {
		return this.jdbc.sql("select company_id from job where id = :id").param("id", jobId).query(Long.class).single();
	}

	private long nicheId(String name) {
		return this.jdbc.sql("select id from niche where name = :name").param("name", name).query(Long.class).single();
	}

	private String segmentOf(String cnpj) {
		return this.jdbc.sql("""
				select s.name from company c join company_niche cn on cn.company_id = c.id
				join niche_segment s on s.id = cn.segment_id where c.cnpj = :cnpj
				""").param("cnpj", cnpj).query(String.class).single();
	}

	private int fitOf(String cnpj) {
		return this.jdbc.sql("select f.fit_score from company_fit f join company c on c.id = f.company_id where c.cnpj = :cnpj")
			.param("cnpj", cnpj)
			.query(Integer.class)
			.single();
	}

	private long count(String sql) {
		return this.jdbc.sql(sql).query(Long.class).single();
	}

}
