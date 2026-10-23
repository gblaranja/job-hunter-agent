package dev.laranja.jobhunter.resumes;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import com.jayway.jsonpath.JsonPath;
import dev.laranja.jobhunter.IntegrationTest;
import dev.laranja.jobhunter.TestFiles;
import org.junit.jupiter.api.Test;

import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ResumeUploadIntegrationTests extends IntegrationTest {

	@Test
	void uploadedResumeIsDownloadedThroughAPresignedUrl() throws Exception {
		byte[] content = TestFiles.pdf("download");
		long id = uploadResume(content, "Currículo Gabriel.pdf", "Backend");

		this.mvc.perform(get("/api/resumes").with(owner()))
			.andExpect(jsonPath("$[0].id").value(id))
			.andExpect(jsonPath("$[0].isDefault").value(true))
			.andExpect(jsonPath("$[0].contentType").value("application/pdf"));

		String response = this.mvc.perform(get("/api/resumes/{id}/file", id).with(owner()))
			.andExpect(status().isOk())
			.andReturn()
			.getResponse()
			.getContentAsString();
		URI url = URI.create(JsonPath.read(response, "$.url"));

		try (HttpClient http = HttpClient.newHttpClient()) {
			HttpResponse<byte[]> download = http.send(HttpRequest.newBuilder(url).build(),
					HttpResponse.BodyHandlers.ofByteArray());
			assertThat(download.statusCode()).isEqualTo(200);
			assertThat(download.body()).isEqualTo(content);
			assertThat(download.headers().firstValue("Content-Disposition")).hasValueSatisfying(
					disposition -> assertThat(disposition).contains("inline").contains("Curr"));
		}
	}

	@Test
	void sameFileTwiceIsAConflict() throws Exception {
		byte[] content = TestFiles.pdf("dup");
		long id = uploadResume(content, "cv.pdf", "Primeiro");

		this.mvc.perform(multipart("/api/resumes").file(new MockMultipartFile("file", "outro-nome.pdf", null, content))
			.with(owner())).andExpect(status().isConflict()).andExpect(jsonPath("$.existingId").value(id));
	}

	@Test
	void fileTypeComesFromTheContentNotTheName() throws Exception {
		this.mvc
			.perform(multipart("/api/resumes")
				.file(new MockMultipartFile("file", "curriculo.pdf", "application/pdf", TestFiles.executable()))
				.with(owner()))
			.andExpect(status().is(422))
			.andExpect(jsonPath("$.detail").value(containsString("PDF ou DOCX")));

		long id = uploadResume(TestFiles.docx("docx"), "curriculo.pdf", "Word");
		this.mvc.perform(get("/api/resumes").with(owner()))
			.andExpect(jsonPath("$[?(@.id == %d)].contentType".formatted(id)).value(
					"application/vnd.openxmlformats-officedocument.wordprocessingml.document"));
	}

	@Test
	void defaultResumeMovesAndCannotBeArchived() throws Exception {
		long first = uploadResume(TestFiles.pdf("first"), "a.pdf", "A");
		long second = uploadResume(TestFiles.pdf("second"), "b.pdf", "B");

		this.mvc
			.perform(patch("/api/resumes/{id}", first).with(owner()).contentType(MediaType.APPLICATION_JSON).content("""
					{"archived": true}
					"""))
			.andExpect(status().is(422));

		this.mvc
			.perform(patch("/api/resumes/{id}", second).with(owner()).contentType(MediaType.APPLICATION_JSON).content("""
					{"makeDefault": true, "label": "B revisado"}
					"""))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.isDefault").value(true))
			.andExpect(jsonPath("$.label").value("B revisado"));

		this.mvc
			.perform(patch("/api/resumes/{id}", first).with(owner()).contentType(MediaType.APPLICATION_JSON).content("""
					{"archived": true}
					"""))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.isDefault").value(false))
			.andExpect(jsonPath("$.archived").value(true));
	}

}
