package dev.laranja.jobhunter.network;

import com.jayway.jsonpath.JsonPath;
import dev.laranja.jobhunter.IntegrationTest;
import org.junit.jupiter.api.Test;

import org.springframework.http.MediaType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Contatos casados com a empresa pelo nome, conversas na candidatura e exclusão com o histórico (D-05). */
class ContactsIntegrationTests extends IntegrationTest {

	@Test
	void aRecruiterTalkAboutAnApplicationShowsUpOnTheApplicationAndDeletingTheContactErasesIt() throws Exception {
		long jobId = createJob("Desenvolvedor Java", "Banco Exemplo S.A.", "Java");
		long applicationId = startApplication(jobId, "{\"status\": \"INTERESSE\"}");
		long contactId = createContact("""
				{"name": "Ana Lima", "role": "RECRUTADOR", "company": "BANCO EXEMPLO", "position": "Tech Recruiter",
				 "linkedinUrl": "http://br.linkedin.com/in/Ana-Lima/?trk=x"}
				""");

		this.mvc.perform(get("/api/contacts/{id}", contactId).with(owner()))
			.andExpect(jsonPath("$.companyId").isNumber())
			.andExpect(jsonPath("$.linkedinUrl").value("https://www.linkedin.com/in/ana-lima"))
			.andExpect(jsonPath("$.source").value("MANUAL"));
		assertThat(count("select count(*) from company")).isEqualTo(1);

		this.mvc.perform(post("/api/contacts/{id}/interactions", contactId).with(owner())
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"channel": "LINKEDIN", "direction": "RECEBIDA", "kind": "CONVERSA",
					 "summary": "Falou da vaga de Java", "applicationId": %d}
					""".formatted(applicationId)))
			.andExpect(status().isCreated());

		this.mvc.perform(get("/api/applications/{id}/contacts", applicationId).with(owner()))
			.andExpect(jsonPath("$.contacts", hasSize(1)))
			.andExpect(jsonPath("$.contacts[0].name").value("Ana Lima"))
			.andExpect(jsonPath("$.interactions[0].summary").value("Falou da vaga de Java"))
			.andExpect(jsonPath("$.interactions[0].contactName").value("Ana Lima"));
		this.mvc.perform(get("/api/jobs/{id}/contacts", jobId).with(owner()))
			.andExpect(jsonPath("$[0].role").value("RECRUTADOR"));
		this.mvc.perform(get("/api/contacts").param("q", "banco").with(owner()))
			.andExpect(jsonPath("$.total").value(1))
			.andExpect(jsonPath("$.items[0].lastInteractionAt").isNotEmpty());

		this.mvc.perform(delete("/api/contacts/{id}", contactId).with(owner())).andExpect(status().isNoContent());

		assertThat(count("select count(*) from contact")).isZero();
		assertThat(count("select count(*) from contact_interaction")).isZero();
	}

	@Test
	void aContactAtAnUnknownCompanyDoesNotCreateTheCompany() throws Exception {
		long contactId = createContact("""
				{"name": "Bruno Souza", "role": "HEADHUNTER", "company": "Agência Talentos"}
				""");

		this.mvc.perform(get("/api/contacts/{id}", contactId).with(owner()))
			.andExpect(jsonPath("$.companyId").doesNotExist())
			.andExpect(jsonPath("$.companyName").value("Agência Talentos"));
		assertThat(count("select count(*) from company")).isZero();
	}

	@Test
	void theSameProfileCannotBeRegisteredTwice() throws Exception {
		createContact("""
				{"name": "Ana Lima", "role": "GESTOR", "linkedinUrl": "https://www.linkedin.com/in/ana-lima/"}
				""");

		this.mvc.perform(post("/api/contacts").with(owner())
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"name\": \"Ana\", \"role\": \"OUTRO\", \"linkedinUrl\": \"https://linkedin.com/in/ana-lima\"}"))
			.andExpect(status().isConflict());

		long other = createContact("{\"name\": \"Carla\", \"role\": \"OUTRO\"}");
		this.mvc.perform(put("/api/contacts/{id}", other).with(owner())
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"name\": \"Carla\", \"role\": \"INDICACAO\", \"notes\": \"Ex-colega\"}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.role").value("INDICACAO"))
			.andExpect(jsonPath("$.notes").value("Ex-colega"));
	}

	private long createContact(String body) throws Exception {
		String response = this.mvc.perform(post("/api/contacts").with(owner())
			.contentType(MediaType.APPLICATION_JSON)
			.content(body))
			.andExpect(status().isCreated())
			.andReturn()
			.getResponse()
			.getContentAsString();
		return ((Number) JsonPath.read(response, "$.id")).longValue();
	}

	private long count(String sql) {
		return this.jdbc.sql(sql).query(Long.class).single();
	}

}
