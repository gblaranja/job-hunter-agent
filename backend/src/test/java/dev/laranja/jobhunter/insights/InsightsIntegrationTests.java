package dev.laranja.jobhunter.insights;

import dev.laranja.jobhunter.IntegrationTest;
import org.junit.jupiter.api.Test;

import static org.hamcrest.Matchers.closeTo;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class InsightsIntegrationTests extends IntegrationTest {

	@Test
	void reportAggregatesTheWeeksAndFallsBackToRulesWithoutTheLlm() throws Exception {
		this.mvc.perform(get("/api/insights/latest").with(owner())).andExpect(status().isNoContent());
		InsightFixtures.seed(this.jdbc);

		this.mvc.perform(post("/api/insights/generate").with(owner()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.topSkills[0].skill").value("Java"))
			.andExpect(jsonPath("$.topSkills[0].jobs").value(12))
			.andExpect(jsonPath("$.topSkills[0].covered").value(true))
			.andExpect(jsonPath("$.topSkills[1].skill").value("Kafka"))
			.andExpect(jsonPath("$.topSkills[1].covered").value(false))
			.andExpect(jsonPath("$.gaps[0].skill").value("Kafka"))
			.andExpect(jsonPath("$.gaps[0].jobs").value(8))
			.andExpect(jsonPath("$.gaps[0].avgScore").value(62))
			.andExpect(jsonPath("$.funnel.applied").value(6))
			.andExpect(jsonPath("$.funnel.screening").value(2))
			.andExpect(jsonPath("$.funnel.interview").value(2))
			.andExpect(jsonPath("$.funnel.offer").value(1))
			.andExpect(jsonPath("$.funnel.avgScoreApplied").value(62))
			.andExpect(jsonPath("$.funnel.scoreByRating.NAO_GOSTEI").value(77))
			.andExpect(jsonPath("$.resumePerformance[0].label").value("Backend 2026"))
			.andExpect(jsonPath("$.resumePerformance[0].applied").value(5))
			.andExpect(jsonPath("$.resumePerformance[0].interview").value(2))
			.andExpect(jsonPath("$.resumePerformance[1].label").value("CV antigo"))
			.andExpect(jsonPath("$.suggestedFilters[?(@.reason == 'SENIORIDADE')].keyword").value(hasItem("júnior")))
			.andExpect(jsonPath("$.suggestedFilters[?(@.reason == 'EMPRESA')].title").value(hasItem("Ignore Agência Beta")))
			.andExpect(jsonPath("$.aiAccuracy").value(closeTo(0.8, 0.001)))
			.andExpect(jsonPath("$.llmCostUsd").value(closeTo(0.10, 0.0001)))
			.andExpect(jsonPath("$.recommendations.source").value("REGRAS"))
			.andExpect(jsonPath("$.recommendations.gaps[0].skill").value("Kafka"))
			.andExpect(jsonPath("$.recommendations.linkedinKeywords").value(hasItem("Spring Boot")))
			// 2 de 6 chegaram à entrevista (33%): o funil está saudável.
			.andExpect(jsonPath("$.recommendations.diagnosis").value("EQUILIBRADO"))
			.andExpect(jsonPath("$.recommendations.linkedinProfile.headline").value(containsString("Spring Boot")))
			.andExpect(jsonPath("$.recommendations.gupyProfile.skills").value(containsString("Spring Boot")))
			.andExpect(jsonPath("$.modelVersion").value("regras@v2"));

		this.mvc.perform(get("/api/insights/latest").with(owner()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.gaps[0].skill").value("Kafka"));
		this.mvc.perform(get("/api/insights/usage").with(owner()))
			.andExpect(jsonPath("$", hasSize(1)))
			.andExpect(jsonPath("$[0].task").value("match-score"))
			.andExpect(jsonPath("$[0].calls").value(1));
	}

}
