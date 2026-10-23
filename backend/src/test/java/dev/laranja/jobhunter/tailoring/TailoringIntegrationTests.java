package dev.laranja.jobhunter.tailoring;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.jayway.jsonpath.JsonPath;
import dev.laranja.jobhunter.AnthropicStubConfiguration;
import dev.laranja.jobhunter.IntegrationTest;
import dev.laranja.jobhunter.TestFiles;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static dev.laranja.jobhunter.AnthropicStubConfiguration.messageWith;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Import(AnthropicStubConfiguration.class)
class TailoringIntegrationTests extends IntegrationTest {

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
			.willReturn(okJson(messageWith("""
					{"skills": ["Java", "Spring Boot", "Kafka", "Docker"], "seniority": "SENIOR"}
					"""))));
		this.anthropicApi.stubFor(WireMock.post("/v1/messages").withRequestBody(containing("analista de vagas"))
			.willReturn(okJson(messageWith("""
					{"seniority": "SENIOR", "requiredSkills": ["Java", "Spring Boot", "Kafka", "AWS"],
					 "niceToHave": ["Kubernetes"], "workModel": "REMOTO", "summary": "Motor de crédito.",
					 "employerKind": "CONTRATANTE", "clientSectors": [], "contractType": "CLT"}
					"""))));
		this.anthropicApi.stubFor(WireMock.post("/v1/messages").withRequestBody(containing("avalia a aderência"))
			.willReturn(okJson(messageWith("""
					{"adjustment": 0, "justification": "Sem ajuste."}
					"""))));
		this.anthropicApi.stubFor(WireMock.post("/v1/messages").withRequestBody(containing("sistemas de triagem (ATS)"))
			.willReturn(okJson(messageWith("""
					{"headline": "Desenvolvedor Backend Java Sênior",
					 "markdown": "# Ana Lima\\n## Resumo\\nDesenvolvedor backend Java com Spring Boot, Kafka e AWS.\\n## Experiência\\n### Desenvolvedor Sênior · Banco\\n- Eventos com Kafka processando [X] transações.",
					 "changes": [{"section": "Resumo", "before": "", "after": "Desenvolvedor backend Java com Spring Boot.",
					              "reason": "A vaga pede Java e Spring Boot."}],
					 "notCovered": ["Kubernetes"],
					 "notes": "Preencha o volume de transações."}
					"""))));
	}

	@Test
	void rewritesTheResumeForTheJobAndSavesItAsAResumeVersion() throws Exception {
		long jobId = analyzedJob();

		String created = this.mvc.perform(post("/api/jobs/{jobId}/tailored-resumes", jobId).with(owner()))
			.andExpect(status().isAccepted())
			.andExpect(jsonPath("$.status").value("GERANDO"))
			.andExpect(jsonPath("$.baseResumeLabel").value("Backend Java"))
			.andReturn()
			.getResponse()
			.getContentAsString();
		long id = ((Number) JsonPath.read(created, "$.id")).longValue();
		await(() -> "PRONTO".equals(statusOf(id)));

		// Antes: obrigatórias 3/4 (53) + senioridade 10 + remoto 5 = 68; depois, com AWS no texto: 70 + 10 + 5 = 85
		this.mvc.perform(get("/api/jobs/{jobId}/tailored-resumes", jobId).with(owner()))
			.andExpect(jsonPath("$[0].headline").value("Desenvolvedor Backend Java Sênior"))
			.andExpect(jsonPath("$[0].content").value(containsString("# Ana Lima\n## Resumo")))
			.andExpect(jsonPath("$[0].keywords.length()").value(5))
			.andExpect(jsonPath("$[0].coveredBefore.length()").value(3))
			.andExpect(jsonPath("$[0].coveredAfter.length()").value(4))
			.andExpect(jsonPath("$[0].scoreBefore").value(68))
			.andExpect(jsonPath("$[0].scoreAfter").value(85))
			.andExpect(jsonPath("$[0].unsupportedSkills[0]").value("AWS"))
			.andExpect(jsonPath("$[0].notCovered[0]").value("Kubernetes"))
			.andExpect(jsonPath("$[0].changes[0].reason").value("A vaga pede Java e Spring Boot."))
			.andExpect(jsonPath("$[0].modelVersion").value("claude-opus-5-5|tailor-resume@v1"));

		// Modelo maior com pensamento; o currículo base vai no bloco de sistema e a vaga com a análise na mensagem.
		this.anthropicApi.verify(postRequestedFor(urlPathEqualTo("/v1/messages"))
			.withRequestBody(containing("\"claude-opus-5-5\""))
			.withRequestBody(containing("\"adaptive\""))
			.withRequestBody(containing("Spring Boot Kafka e Docker"))
			.withRequestBody(containing("Lacunas: AWS")));
		assertThat(count("select count(*) from notification where type = 'CURRICULO_ADAPTADO'")).isEqualTo(1);

		this.mvc.perform(get("/api/tailored-resumes/{id}/docx", id).with(owner()))
			.andExpect(status().isOk())
			.andExpect(header().string("Content-Disposition",
					containsString("curriculo-fintech-de-credito-desenvolvedor-java-senior.docx")));

		this.mvc.perform(post("/api/tailored-resumes/{id}/resume-version", id).with(owner()))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.label").value("Desenvolvedor Java Sênior · Fintech de Crédito"));
		assertThat(count("select count(*) from resume_version where content_type like '%wordprocessingml%'")).isEqualTo(1);
		this.mvc.perform(get("/api/resumes").with(owner()))
			.andExpect(jsonPath("$[?(@.tailoredForJobId == " + jobId + ")].label")
				.value("Desenvolvedor Java Sênior · Fintech de Crédito"));
		this.mvc.perform(post("/api/tailored-resumes/{id}/resume-version", id).with(owner()))
			.andExpect(status().isConflict());

		// Editar recalcula a cobertura e libera salvar o texto novo como outra versão.
		this.mvc.perform(put("/api/tailored-resumes/{id}/content", id).with(owner())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"content\": \"# Ana Lima\\nJava, Spring Boot, Kafka e Kubernetes.\"}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.edited").value(true))
			.andExpect(jsonPath("$.resumeVersionId").doesNotExist())
			.andExpect(jsonPath("$.coveredAfter.length()").value(4))
			.andExpect(jsonPath("$.unsupportedSkills[0]").value("Kubernetes"));
	}

	@Test
	void llmFailureMarksTheVersionAsFailed() throws Exception {
		long jobId = analyzedJob();
		this.anthropicApi.stubFor(WireMock.post("/v1/messages").withRequestBody(containing("sistemas de triagem (ATS)"))
			.willReturn(aResponse().withStatus(500)));

		String created = this.mvc.perform(post("/api/jobs/{jobId}/tailored-resumes", jobId).with(owner()))
			.andExpect(status().isAccepted())
			.andReturn()
			.getResponse()
			.getContentAsString();
		long id = ((Number) JsonPath.read(created, "$.id")).longValue();
		await(() -> "FALHOU".equals(statusOf(id)));

		this.mvc.perform(get("/api/tailored-resumes/{id}", id).with(owner()))
			.andExpect(jsonPath("$.lastError").value(containsString("Claude API")));
		this.mvc.perform(post("/api/tailored-resumes/{id}/resume-version", id).with(owner()))
			.andExpect(status().isUnprocessableContent());
	}

	@Test
	void markingInterestGeneratesTheFirstVersion() throws Exception {
		long jobId = analyzedJob();

		startApplication(jobId, "{\"status\": \"INTERESSE\"}");

		await(() -> count("select count(*) from tailored_resume where status = 'PRONTO' and job_id = " + jobId) == 1);
	}

	private long analyzedJob() throws Exception {
		uploadResume(TestFiles.pdfWithText("Desenvolvedor Java Senior com Spring Boot Kafka e Docker"), "cv.pdf",
				"Backend Java");
		await(() -> count("select count(*) from resume_version where jsonb_array_length(skills) > 0") == 1);
		long jobId = createJob("Desenvolvedor Java Sênior", "Fintech de Crédito", DESCRIPTION);
		await(() -> count("select count(*) from job_analysis where status = 'COMPLETO' and job_id = " + jobId) == 1);
		return jobId;
	}

	private String statusOf(long id) {
		return this.jdbc.sql("select status from tailored_resume where id = :id").param("id", id).query(String.class).single();
	}

	private long count(String sql) {
		return this.jdbc.sql(sql).query(Long.class).single();
	}

}
