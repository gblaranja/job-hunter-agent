package dev.laranja.jobhunter.companies;

import dev.laranja.jobhunter.IntegrationTest;
import org.junit.jupiter.api.Test;

import org.springframework.http.MediaType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CompaniesAndFeedbackIntegrationTests extends IntegrationTest {

	@Test
	void jobsShareTheCompanyAndMergedDuplicatesBecomeAliases() throws Exception {
		createJob("Dev Java", "Itaú Unibanco S.A.", "Java");
		createJob("Dev Kotlin", "ITAU UNIBANCO", "Kotlin");
		createJob("Arquiteto", "Itaú", "AWS");

		this.mvc.perform(get("/api/companies").with(owner())).andExpect(jsonPath("$", hasSize(2)));
		long unibanco = companyId("itau unibanco");
		long itau = companyId("itau");

		this.mvc.perform(post("/api/companies/{id}/merge", itau).with(owner())
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"targetId\": " + unibanco + "}")).andExpect(status().isNoContent());

		this.mvc.perform(get("/api/companies/{id}", unibanco).with(owner()))
			.andExpect(jsonPath("$.jobsTotal").value(3));
		// Depois da mescla, uma vaga nova de "Itaú" cai direto na empresa de destino.
		createJob("SRE", "Itau", "Kubernetes");
		this.mvc.perform(get("/api/companies").with(owner())).andExpect(jsonPath("$", hasSize(1)));

		this.mvc
			.perform(patch("/api/companies/{id}", unibanco).with(owner())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"watchStatus": "ALVO", "atsType": "GUPY", "atsSlug": "itau"}
						"""))
			.andExpect(jsonPath("$.watchStatus").value("ALVO"))
			.andExpect(jsonPath("$.atsSlug").value("itau"));
	}

	private long companyId(String normalizedName) {
		return this.jdbc.sql("select id from company where normalized_name = :name")
			.param("name", normalizedName)
			.query(Long.class)
			.single();
	}

	@Test
	void feedbackIsUpsertedAndDislikedJobsLeaveTheInbox() throws Exception {
		long liked = createJob("Dev Java Sênior", "Banco A", "Java");
		long disliked = createJob("Dev PHP", "Agência B", "PHP");

		this.mvc.perform(put("/api/jobs/{id}/feedback", disliked).with(owner())
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"rating": "NEUTRO", "reasons": []}
					""")).andExpect(status().isNoContent());
		this.mvc.perform(put("/api/jobs/{id}/feedback", disliked).with(owner())
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"rating": "NAO_GOSTEI", "reasons": ["STACK", "SALARIO"], "comment": "Stack fora do meu perfil"}
					""")).andExpect(status().isNoContent());
		this.mvc.perform(put("/api/jobs/{id}/feedback", liked).with(owner())
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"rating": "GOSTEI"}
					""")).andExpect(status().isNoContent());

		this.mvc.perform(get("/api/jobs").param("inbox", "true").with(owner()))
			.andExpect(jsonPath("$.total").value(1))
			.andExpect(jsonPath("$.items[0].id").value(liked))
			.andExpect(jsonPath("$.items[0].rating").value("GOSTEI"));
		this.mvc.perform(get("/api/jobs/{id}", disliked).with(owner()))
			.andExpect(jsonPath("$.feedback.rating").value("NAO_GOSTEI"))
			.andExpect(jsonPath("$.feedback.reasons", hasSize(2)))
			.andExpect(jsonPath("$.feedback.comment").value("Stack fora do meu perfil"));
		assertThat(this.jdbc.sql("select count(*) from job_feedback").query(Long.class).single()).isEqualTo(2);
	}

	@Test
	void dismissedJobsLeaveTheInboxUntilRestored() throws Exception {
		long kept = createJob("Dev Java Sênior", "Banco A", "Java");
		long notInterested = createJob("Dev Java Pleno", "Varejo B", "Java");
		long offProfile = createJob("Cientista de Dados", "Fintech C", "Python");

		this.mvc.perform(put("/api/jobs/{id}/dismissal", notInterested).with(owner())
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"reason": "SEM_INTERESSE"}
					""")).andExpect(status().isNoContent());
		this.mvc.perform(put("/api/jobs/{id}/dismissal", offProfile).with(owner())
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"reason": "SEM_PERFIL_TECNICO"}
					""")).andExpect(status().isNoContent());

		this.mvc.perform(get("/api/jobs").param("inbox", "true").with(owner()))
			.andExpect(jsonPath("$.total").value(1))
			.andExpect(jsonPath("$.items[0].id").value(kept));
		this.mvc.perform(get("/api/jobs/{id}", offProfile).with(owner()))
			.andExpect(jsonPath("$.dismissal.reason").value("SEM_PERFIL_TECNICO"));

		this.mvc.perform(delete("/api/jobs/{id}/dismissal", notInterested).with(owner()))
			.andExpect(status().isNoContent());
		this.mvc.perform(get("/api/jobs").param("inbox", "true").with(owner()))
			.andExpect(jsonPath("$.total").value(2));
		this.mvc.perform(get("/api/jobs/{id}", notInterested).with(owner()))
			.andExpect(jsonPath("$.dismissal").doesNotExist());

		this.mvc.perform(put("/api/jobs/{id}/dismissal", 999_999).with(owner())
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"reason": "SEM_INTERESSE"}
					""")).andExpect(status().isNotFound());
	}

	@Test
	void searchProfilesAreEditable() throws Exception {
		this.mvc.perform(get("/api/search-profiles").with(owner()))
			.andExpect(jsonPath("$[0].excludedKeywords[0]").value("PHP"));

		this.mvc
			.perform(post("/api/search-profiles").with(owner()).contentType(MediaType.APPLICATION_JSON).content("""
					{"name": "Remoto internacional", "keywords": ["Java", " Kotlin ", ""], "excludedKeywords": [],
					 "locations": [], "remoteOnly": true, "active": false}
					"""))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.keywords", hasSize(2)))
			.andExpect(jsonPath("$.keywords[1]").value("Kotlin"));
	}

}
