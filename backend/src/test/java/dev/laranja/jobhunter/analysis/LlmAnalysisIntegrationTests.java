package dev.laranja.jobhunter.analysis;


import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import dev.laranja.jobhunter.AnthropicStubConfiguration;
import dev.laranja.jobhunter.IntegrationTest;
import dev.laranja.jobhunter.TestFiles;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

@Import(AnthropicStubConfiguration.class)
class LlmAnalysisIntegrationTests extends IntegrationTest {

	private static final String DESCRIPTION = """
			Buscamos pessoa desenvolvedora backend para o motor de crédito. Requisitos: Java 21, Spring Boot, Kafka
			e AWS. Diferenciais: Kubernetes. Contratação CLT, remoto.
			""";

	@Autowired
	private WireMockServer anthropicApi;

	@BeforeEach
	void stubClaude() {
		this.anthropicApi.resetAll();
		this.anthropicApi.stubFor(WireMock.post("/v1/messages").withRequestBody(containing("currículos de profissionais"))
			.willReturn(okJson(AnthropicStubConfiguration.messageWith("""
					{"skills": ["Java", "springboot", "Kafka", "Docker"], "seniority": "SENIOR"}
					"""))));
		this.anthropicApi.stubFor(WireMock.post("/v1/messages").withRequestBody(containing("analista de vagas"))
			.willReturn(okJson(AnthropicStubConfiguration.messageWith("""
					{"seniority": "SENIOR", "requiredSkills": ["Java", "Spring Boot", "Kafka", "AWS"],
					 "niceToHave": ["k8s"], "workModel": "REMOTO",
					 "summary": "Evoluir o motor de crédito em Java e Spring Boot com eventos em Kafka.",
					 "employerKind": "CONTRATANTE", "clientSectors": [], "contractType": "CLT",
					 "knockouts": [{"type": "IDIOMA", "text": "Inglês avançado", "value": "INGLES:AVANCADO"},
					               {"type": "XPTO", "text": "ignorado", "value": "x"}],
					 "salary": {"min": 15000, "max": 18000, "currency": "BRL", "period": "MENSAL", "regime": "CLT",
					            "text": "R$ 15 mil a R$ 18 mil"},
					 "jobFamily": "BACKEND", "internationalEligibility": "BRASIL"}
					"""))));
		this.anthropicApi.stubFor(WireMock.post("/v1/messages").withRequestBody(containing("avalia a aderência"))
			.willReturn(okJson(AnthropicStubConfiguration.messageWith("""
					{"adjustment": 5, "justification": "Experiência com motores de decisão compensa a falta de AWS.",
					 "evidence": [{"requirement": "Kafka", "quote": "Spring Boot, Kafka e Docker"},
					              {"requirement": "AWS", "quote": "5 anos de AWS em produção"}]}
					"""))));
	}

	@Test
	void llmRefinesExtractionAndAdjustsTheDeterministicScore() throws Exception {
		uploadResume(TestFiles.pdfWithText("Desenvolvedor Java Senior com Spring Boot Kafka e Docker"), "cv.pdf",
				"Backend Java Sênior");
		await(() -> count("select count(*) from resume_version where jsonb_array_length(skills) > 0") == 1);
		assertThat(this.jdbc.sql("select skills::text from resume_version").query(String.class).single())
			.contains("Spring Boot").doesNotContain("springboot");

		long jobId = createJob("Desenvolvedor Java Sênior", "Fintech de Crédito", DESCRIPTION);
		await(() -> count("select count(*) from job_analysis where status = 'COMPLETO' and job_id = " + jobId) == 1);

		// Determinístico: obrigatórias 3/4 (53), desejáveis: Kubernetes em parte por Docker (8),
		// senioridade 10, remoto 5 = 76; LLM +5
		this.mvc.perform(get("/api/jobs/{id}", jobId).with(owner()))
			.andExpect(jsonPath("$.analysis.status").value("COMPLETO"))
			.andExpect(jsonPath("$.analysis.niceToHave[0]").value("Kubernetes"))
			.andExpect(jsonPath("$.analysis.deterministicScore").value(76))
			.andExpect(jsonPath("$.analysis.llmAdjustment").value(5))
			.andExpect(jsonPath("$.analysis.matchScore").value(81))
			.andExpect(jsonPath("$.analysis.gaps[0]").value("AWS"))
			.andExpect(jsonPath("$.analysis.justification").value(containsString("motores de decisão")))
			.andExpect(jsonPath("$.analysis.modelVersion").value(containsString("extract-requirements@v2")))
			.andExpect(jsonPath("$.analysis.knockouts[0].type").value("IDIOMA"))
			.andExpect(jsonPath("$.analysis.knockouts[0].status").value("SEM_INFORMACAO"))
			.andExpect(jsonPath("$.analysis.knockouts[1].type").value("CONTRATO"))
			.andExpect(jsonPath("$.analysis.salary.min").value(15000))
			.andExpect(jsonPath("$.analysis.jobFamily").value("BACKEND"))
			// a citação inventada (AWS) não está no currículo e é descartada
			.andExpect(jsonPath("$.analysis.evidence.length()").value(1))
			.andExpect(jsonPath("$.analysis.evidence[0].requirement").value("Kafka"))
			.andExpect(jsonPath("$.analysis.internationalEligibility").value("BRASIL"));
		this.mvc.perform(get("/api/jobs").param("inbox", "true").with(owner()))
			.andExpect(jsonPath("$.items[0].matchScore").value(81))
			.andExpect(jsonPath("$.items[0].summary").value(containsString("motor de crédito")));

		// O currículo vai no bloco de sistema com cache_control; o uso de tokens fica registrado.
		this.anthropicApi.verify(postRequestedFor(urlPathEqualTo("/v1/messages"))
			.withRequestBody(containing("\"cache_control\""))
			.withRequestBody(containing("Spring Boot Kafka e Docker")));
		assertThat(count("select count(*) from llm_usage where cache_read_tokens = 1000")).isGreaterThanOrEqualTo(3);
	}

	@Test
	void llmFailureKeepsTheDeterministicAnalysisAndSchedulesARetry() throws Exception {
		this.anthropicApi.resetAll();
		this.anthropicApi.stubFor(WireMock.post("/v1/messages").willReturn(aResponse().withStatus(500)));

		long jobId = createJob("Desenvolvedor Java Sênior", "Banco Exemplo", DESCRIPTION);
		await(() -> count("select count(*) from job_analysis where job_id = " + jobId) == 1);

		this.mvc.perform(get("/api/jobs/{id}", jobId).with(owner()))
			.andExpect(jsonPath("$.analysis.status").value("FALHOU"))
			.andExpect(jsonPath("$.analysis.requiredSkills[0]").value("Java"))
			.andExpect(jsonPath("$.analysis.lastError").value(containsString("Claude API")));
		assertThat(count("select attempts from job_analysis where job_id = " + jobId)).isEqualTo(1);
		assertThat(count("select count(*) from job_analysis where next_attempt_at > now()")).isEqualTo(1);
	}

	private long count(String sql) {
		return this.jdbc.sql(sql).query(Long.class).single();
	}

}
