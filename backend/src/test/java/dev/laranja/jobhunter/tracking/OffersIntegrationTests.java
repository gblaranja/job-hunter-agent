package dev.laranja.jobhunter.tracking;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.jayway.jsonpath.JsonPath;
import dev.laranja.jobhunter.IntegrationTest;
import dev.laranja.jobhunter.TestFiles;
import dev.laranja.jobhunter.shared.money.PtaxClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class OffersIntegrationTests extends IntegrationTest {

	@Autowired
	private WireMockServer googleApi;

	@Autowired
	private PtaxClient ptax;

	@BeforeEach
	void stubPtax() {
		this.googleApi.resetAll();
		// Outro teste do mesmo contexto pode ter guardado a falha do câmbio (sem stub).
		this.ptax.evictCache();
		this.googleApi.stubFor(WireMock.get(urlPathMatching("/CotacaoDolarDia.*"))
			.withQueryParam("$format", equalTo("json"))
			.willReturn(okJson("""
					{"@odata.context": "x", "value": [{"cotacaoCompra": 4.99, "cotacaoVenda": 5.0,
					 "dataHoraCotacao": "2026-10-23 13:04:28.123"}]}
					""")));
		this.googleApi.stubFor(WireMock.get(urlPathMatching("/CotacaoMoedaDia.*")).willReturn(aResponse().withStatus(503)));
	}

	@Test
	void comparesCltAndPjOffersOnTheSameAnnualScaleAndCountsTheAcceptedOne() throws Exception {
		long cltApplication = interviewing("Desenvolvedor Java", "Banco Alfa");
		long pjApplication = interviewing("Desenvolvedor Kotlin", "Fintech Beta");
		long usdApplication = interviewing("Backend Engineer", "Remote Co");

		long clt = create(cltApplication, """
				{"regime": "CLT", "baseSalary": 10000, "monthlyBenefits": 1500, "workModel": "REMOTO"}
				""");
		long pj = create(pjApplication, """
				{"regime": "PJ", "baseSalary": 15000, "currency": "brl", "respondBy": "2026-11-05"}
				""");
		create(usdApplication, """
				{"regime": "CONTRACTOR", "baseSalary": 40, "currency": "USD", "period": "HORA"}
				""");
		long eur = create(usdApplication, """
				{"regime": "CONTRACTOR", "baseSalary": 5000, "currency": "EUR"}
				""");

		// CLT: 10.000 × 13,33 × 1,08 + 1.500 × 12 = 161.964; PJ: (15.000 × 0,85 − 400) × 12 = 148.200;
		// US$ 40/h × 168 × 5,00 = R$ 33.600 por mês → (33.600 × 0,85 − 400) × 12 = 337.920; o euro ficou sem câmbio.
		this.mvc.perform(get("/api/offers").with(owner()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.length()").value(4))
			.andExpect(jsonPath("$[?(@.id == " + clt + ")].annualEquivalent").value(161964.0))
			.andExpect(jsonPath("$[?(@.id == " + clt + ")].company").value("Banco Alfa"))
			.andExpect(jsonPath("$[?(@.id == " + pj + ")].annualEquivalent").value(148200.0))
			.andExpect(jsonPath("$[?(@.id == " + pj + ")].currency").value("BRL"))
			.andExpect(jsonPath("$[?(@.regime == 'CONTRACTOR' && @.currency == 'USD')].annualEquivalent").value(337920.0))
			.andExpect(jsonPath("$[?(@.regime == 'CONTRACTOR' && @.currency == 'USD')].exchangeRate").value(5.0))
			.andExpect(jsonPath("$[?(@.id == " + eur + ")].annualEquivalent").value(org.hamcrest.Matchers.contains((Object) null)));

		// Sem a Claude API, o rascunho sai do modelo fixo, só com os números informados.
		this.mvc.perform(post("/api/offers/{id}/negotiation-draft", pj).with(owner())
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"targetSalary": 18000, "arguments": "O escopo inclui liderar o time de pagamentos."}
					"""))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.source").value("MODELO"))
			.andExpect(jsonPath("$.text").value(containsString("R$ 15.000 por mês")))
			.andExpect(jsonPath("$.text").value(containsString("R$ 18.000 por mês")))
			.andExpect(jsonPath("$.text").value(containsString("liderar o time de pagamentos")))
			.andExpect(jsonPath("$.text").value(not(containsString("Banco Alfa"))));

		this.mvc.perform(put("/api/offers/{id}", clt).with(owner())
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"regime": "CLT", "baseSalary": 11000, "monthlyBenefits": 1500, "decision": "ACEITA"}
					"""))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.decision").value("ACEITA"))
			.andExpect(jsonPath("$.decidedAt").isNotEmpty())
			.andExpect(jsonPath("$.workModel").doesNotExist());

		this.mvc.perform(get("/api/applications/{id}/offers", cltApplication).with(owner()))
			.andExpect(jsonPath("$.length()").value(1))
			.andExpect(jsonPath("$[0].baseSalary").value(11000.0));
		this.mvc.perform(get("/api/insights/funnel").with(owner()))
			.andExpect(jsonPath("$.applied").value(3))
			.andExpect(jsonPath("$.interview").value(3))
			.andExpect(jsonPath("$.accepted").value(1));

		this.mvc.perform(delete("/api/offers/{id}", eur).with(owner())).andExpect(status().isNoContent());
		this.mvc.perform(delete("/api/offers/{id}", eur).with(owner())).andExpect(status().isNotFound());
	}

	@Test
	void rejectsInvalidOffers() throws Exception {
		long application = interviewing("Desenvolvedor Java", "Banco Alfa");
		this.mvc.perform(post("/api/applications/{id}/offers", application).with(owner())
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"regime": "CLT", "baseSalary": 0}
					"""))
			.andExpect(status().isBadRequest());
		this.mvc.perform(post("/api/applications/{id}/offers", 999).with(owner())
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"regime": "CLT", "baseSalary": 1000}
					"""))
			.andExpect(status().isNotFound());
	}

	private long interviewing(String title, String company) throws Exception {
		long jobId = createJob(title, company, "Java, Spring Boot e Kafka. Remoto.");
		long resumeId = uploadResume(TestFiles.pdf(title + company), title + ".pdf", title);
		long applicationId = startApplication(jobId, """
				{"status": "APLICADA", "resumeVersionId": %d}
				""".formatted(resumeId));
		this.mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
			.patch("/api/applications/{id}/status", applicationId)
			.with(owner())
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"status": "ENTREVISTA"}
					"""))
			.andExpect(status().isNoContent());
		return applicationId;
	}

	private long create(long applicationId, String body) throws Exception {
		String response = this.mvc.perform(post("/api/applications/{id}/offers", applicationId).with(owner())
			.contentType(MediaType.APPLICATION_JSON)
			.content(body))
			.andExpect(status().isCreated())
			.andReturn()
			.getResponse()
			.getContentAsString();
		return ((Number) JsonPath.read(response, "$.id")).longValue();
	}

}
