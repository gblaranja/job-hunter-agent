package dev.laranja.jobhunter.analysis;

import java.util.List;

import com.jayway.jsonpath.JsonPath;
import dev.laranja.jobhunter.IntegrationTest;
import dev.laranja.jobhunter.TestFiles;
import org.junit.jupiter.api.Test;

import org.springframework.http.MediaType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SkillReviewIntegrationTests extends IntegrationTest {

	@Test
	void unknownSkillAskedInFiveJobsBecomesASuggestionAndCountsInTheScoreOnceAccepted() throws Exception {
		uploadResume(TestFiles.pdfWithText("Desenvolvedor Java Senior com Elixir e Phoenix"), "cv.pdf", "Backend");
		await(() -> count("select count(*) from resume_version where jsonb_array_length(skills) > 0") == 1);
		for (int i = 0; i < 5; i++) {
			createJob("Desenvolvedor Backend " + i, "Empresa " + i, "Requisitos: Java. Remoto.");
		}
		await(() -> count("select count(*) from job_analysis where match_score is not null") == 5);
		// como a Claude extrairia: uma skill fora do dicionário, com duas grafias
		this.jdbc.sql("""
				update job_analysis set required_skills = case when job_id % 2 = 0 then '["Java", "Elixir"]'::jsonb
				                                               else '["Java", "elixir"]'::jsonb end,
				    model_version = 'claude-haiku-5-5|extract-requirements.v1'
				""").update();
		this.jdbc.sql("update job_analysis set required_skills = '[\"Java\", \"Cobol Legado\"]'::jsonb where job_id = 1")
			.update();

		this.mvc.perform(get("/api/skills/suggestions").with(owner()))
			.andExpect(jsonPath("$", hasSize(1)))
			.andExpect(jsonPath("$[0].name").value("Elixir"))
			.andExpect(jsonPath("$[0].jobCount").value(4))
			.andExpect(jsonPath("$[0].spellings", hasSize(2)));
		this.mvc.perform(get("/api/skills/suggestions").param("minJobs", "1").with(owner()))
			.andExpect(jsonPath("$", hasSize(2)));

		this.mvc.perform(post("/api/skills/reviewed").with(owner()).contentType(MediaType.APPLICATION_JSON).content("""
				{"canonical": "Elixir", "aliases": ["elixir", "elixir lang"],
				 "escoUri": "http://data.europa.eu/esco/skill/abc", "labelPt": "Elixir", "labelEn": "Elixir"}
				""")).andExpect(status().isCreated()).andExpect(jsonPath("$.aliases", hasSize(1)));

		this.mvc.perform(get("/api/skills/suggestions").with(owner())).andExpect(jsonPath("$", hasSize(0)));
		List<String> matched = this.jdbc.sql("select matched_skills::text from job_analysis where job_id = 2")
			.query(String.class)
			.list();
		assertThat(matched.get(0)).contains("Elixir");
		String reviewed = this.mvc.perform(get("/api/skills/reviewed").with(owner()))
			.andReturn()
			.getResponse()
			.getContentAsString();
		assertThat((String) JsonPath.read(reviewed, "$[0].escoUri")).isEqualTo("http://data.europa.eu/esco/skill/abc");

		this.mvc.perform(post("/api/skills/ignored").with(owner()).contentType(MediaType.APPLICATION_JSON).content("""
				{"name": "Cobol legado"}
				""")).andExpect(status().isNoContent());
		this.mvc.perform(get("/api/skills/suggestions").param("minJobs", "1").with(owner()))
			.andExpect(jsonPath("$", hasSize(0)));
		this.mvc.perform(post("/api/skills/reviewed").with(owner()).contentType(MediaType.APPLICATION_JSON).content("""
				{"canonical": "elixir"}
				""")).andExpect(status().isConflict());
	}

	private long count(String sql) {
		Long value = this.jdbc.sql(sql).query(Long.class).single();
		return (value == null) ? 0 : value;
	}

}
