package dev.laranja.jobhunter;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Base dos testes de integração: Postgres e storage S3 reais via Testcontainers, banco limpo a cada teste. */
@SpringBootTest
@AutoConfigureMockMvc
@Import({ TestcontainersConfiguration.class, GoogleApiStubConfiguration.class })
public abstract class IntegrationTest {

	@Autowired
	protected MockMvc mvc;

	@Autowired
	protected JdbcClient jdbc;

	@BeforeEach
	void cleanDatabase() {
		this.jdbc.sql("""
				truncate application_event, job_snapshot, application, job_feedback, job, resume_version, email_message,
				         gmail_sync_state, oauth_credential, collection_run, company_alias, company, job_analysis, llm_usage
				restart identity cascade
				""").update();
	}

	/** Usuário logado com o token CSRF, como o frontend faz. */
	protected static RequestPostProcessor owner() {
		RequestPostProcessor user = user("admin");
		RequestPostProcessor csrf = csrf();
		return request -> csrf.postProcessRequest(user.postProcessRequest(request));
	}

	protected long createJob(String title, String company, String description) throws Exception {
		String body = """
				{"title": %s, "company": %s, "location": "Remoto", "workModel": "REMOTO",
				 "url": "https://example.com/vagas/%d", "description": %s}
				""".formatted(json(title), json(company), System.nanoTime(), json(description));
		String response = this.mvc.perform(post("/api/jobs").with(owner()).contentType(MediaType.APPLICATION_JSON).content(body))
			.andExpect(status().isCreated())
			.andReturn()
			.getResponse()
			.getContentAsString();
		return ((Number) JsonPath.read(response, "$.id")).longValue();
	}

	protected long uploadResume(byte[] content, String fileName, String label) throws Exception {
		var file = new MockMultipartFile("file", fileName, "application/octet-stream", content);
		String response = this.mvc.perform(multipart("/api/resumes").file(file).param("label", label).with(owner()))
			.andExpect(status().isCreated())
			.andReturn()
			.getResponse()
			.getContentAsString();
		return ((Number) JsonPath.read(response, "$.id")).longValue();
	}

	protected long startApplication(long jobId, String body) throws Exception {
		String response = this.mvc
			.perform(post("/api/jobs/{jobId}/application", jobId).with(owner())
				.contentType(MediaType.APPLICATION_JSON)
				.content(body))
			.andExpect(status().isCreated())
			.andReturn()
			.getResponse()
			.getContentAsString();
		return ((Number) JsonPath.read(response, "$.id")).longValue();
	}

	protected static String json(String value) {
		if (value == null) {
			return "null";
		}
		String escaped = value.replace("\\", "\\\\")
			.replace("\"", "\\\"")
			.replace("\n", "\\n")
			.replace("\r", "\\r")
			.replace("\t", "\\t");
		return "\"" + escaped + "\"";
	}

}
