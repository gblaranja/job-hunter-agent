package dev.laranja.jobhunter.analysis.internal;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import dev.laranja.jobhunter.analysis.Seniority;
import dev.laranja.jobhunter.jobs.JobOrigin;
import dev.laranja.jobhunter.jobs.JobView;
import dev.laranja.jobhunter.jobs.WorkModel;
import dev.laranja.jobhunter.shared.ai.AiProperties;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AnalysisRulesTest {

	private final SkillDictionary dictionary = new SkillDictionary();

	@Test
	void dictionaryDetectsWholeWordsAndCanonicalizesSpelling() {
		assertThat(this.dictionary.detect("Experiência com springboot, Apache Kafka, k8s e Postgres. Go-live em março."))
			.containsExactly("Spring Boot", "Kafka", "Kubernetes", "PostgreSQL");
		assertThat(this.dictionary.canonicalize(List.of("springboot", "Spring Boot", "AWS ", "Temporal")))
			.containsExactly("Spring Boot", "AWS", "Temporal");
	}

	@Test
	void seniorityTakesTheHighestLevelMentioned() {
		assertThat(Seniority.detect("Desenvolvedor Java Pleno/Sênior")).hasValue(Seniority.SENIOR);
		assertThat(Seniority.detect("Tech Lead Java")).hasValue(Seniority.ESPECIALISTA);
		assertThat(Seniority.detect("Desenvolvedor Backend")).isEmpty();
		assertThat(Seniority.parse("SENIOR")).hasValue(Seniority.SENIOR);
	}

	@Test
	void deterministicScoreIsExplainable() {
		var requirements = new JobRequirements("SENIOR", List.of("Java", "Spring Boot", "Kafka", "AWS"),
				List.of("Kubernetes", "Terraform"), "REMOTO", null, "CONTRATANTE", List.of(), "CLT");

		var result = new DeterministicScorer().score(requirements, List.of("Java", "Spring Boot", "Kafka", "Kubernetes"),
				Optional.of(Seniority.SENIOR));

		// obrigatórias 3/4 de 70 (53), desejáveis 1/2 de 15 (8), senioridade 10, remoto 5
		assertThat(result.score()).isEqualTo(76);
		assertThat(result.gaps()).containsExactly("AWS");
		assertThat(result.matchedSkills()).containsExactly("Java", "Spring Boot", "Kafka", "Kubernetes");
		assertThat(result.breakdown()).containsEntry("obrigatorias", 53).containsEntry("senioridade", 10);
	}

	@Test
	void distantSeniorityAndOnSiteLowerTheScore() {
		var requirements = new JobRequirements("JUNIOR", List.of("Java"), List.of(), "PRESENCIAL", null, "CONTRATANTE",
				List.of(), "CLT");

		var result = new DeterministicScorer().score(requirements, List.of("Java"), Optional.of(Seniority.SENIOR));

		assertThat(result.breakdown()).containsEntry("senioridade", 0).containsEntry("modeloTrabalho", 1);
	}

	@Test
	void withoutLlmSkillsAfterTheNiceToHaveHeadingAreOptional() {
		var extractor = new RequirementsExtractor(this.dictionary, null, new AiProperties(null, null, null));
		var job = new JobView(1, JobOrigin.MANUAL, "Desenvolvedor Java Sênior", "Banco", "Remoto", WorkModel.REMOTO, null,
				null, """
						Requisitos: Java 21, Spring Boot, Kafka e PostgreSQL. Atuação em microsserviços.
						Diferenciais: Kubernetes, Terraform e Kafka. Contratação PJ.
						""", Instant.now());

		JobRequirements requirements = extractor.deterministic(job);

		assertThat(requirements.requiredSkills()).containsExactly("Java", "Spring Boot", "Kafka", "PostgreSQL", "Microsserviços");
		assertThat(this.dictionary.detect("Spring Boot e Spring Batch")).containsExactly("Spring Boot", "Spring Batch");
		assertThat(requirements.niceToHave()).containsExactly("Kubernetes", "Terraform");
		assertThat(requirements.seniority()).isEqualTo("SENIOR");
		assertThat(requirements.contractType()).isEqualTo("PJ");
	}

}
