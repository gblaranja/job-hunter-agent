package dev.laranja.jobhunter.portability;

import java.nio.charset.StandardCharsets;

import dev.laranja.jobhunter.IntegrationTest;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class DataExportIntegrationTests extends IntegrationTest {

	@Test
	void exportsJobsApplicationsEventsAndContactsAsJsonOrCsv() throws Exception {
		long jobId = createJob("Desenvolvedor Java, Sênior", "Banco \"A\"", "Requisitos: Java e Spring Boot. Remoto.");
		startApplication(jobId, """
				{"status": "INTERESSE", "notes": "=SOMA(1)"}
				""");
		this.jdbc.sql("""
				insert into contact (name, role, source, created_at, updated_at)
				values ('Ana Lima', 'RECRUTADOR', 'MANUAL', now(), now())
				""").update();

		this.mvc.perform(get("/api/export/VAGAS").with(owner()))
			.andExpect(status().isOk())
			.andExpect(header().string("Content-Disposition", containsString("job-hunter-vagas-")))
			.andExpect(jsonPath("$[0].title").value("Desenvolvedor Java, Sênior"))
			.andExpect(jsonPath("$[0].job_family").value("BACKEND"));
		this.mvc.perform(get("/api/export/EVENTOS").with(owner()))
			.andExpect(jsonPath("$[0].to_status").value("INTERESSE"));
		this.mvc.perform(get("/api/export/CONTATOS").with(owner()))
			.andExpect(jsonPath("$[0].name").value("Ana Lima"))
			.andExpect(jsonPath("$[0].interactions").value(0));

		byte[] csv = this.mvc.perform(get("/api/export/CANDIDATURAS").param("format", "csv").with(owner()))
			.andExpect(status().isOk())
			.andExpect(header().string("Content-Type", containsString("text/csv")))
			.andReturn()
			.getResponse()
			.getContentAsByteArray();
		String text = new String(csv, StandardCharsets.UTF_8);
		assertThat(text).startsWith("﻿id,job_id,title,company,status");
		assertThat(text).contains("\"Desenvolvedor Java, Sênior\",\"Banco \"\"A\"\"\",INTERESSE");
		assertThat(text).contains("'=SOMA(1)");

		String empty = this.mvc.perform(get("/api/export/CONVERSAS").param("format", "csv").with(owner()))
			.andReturn()
			.getResponse()
			.getContentAsString(StandardCharsets.UTF_8);
		assertThat(empty).contains("id,contact_id,contact");
		this.mvc.perform(get("/api/export/OUTRO").with(owner())).andExpect(status().is4xxClientError());
	}

}
