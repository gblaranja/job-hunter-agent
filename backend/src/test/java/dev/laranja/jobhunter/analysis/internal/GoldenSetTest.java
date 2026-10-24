package dev.laranja.jobhunter.analysis.internal;

import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import dev.laranja.jobhunter.analysis.internal.GoldenSet.GoldenJob;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class GoldenSetTest {

	@Test
	void loadsTheAnnotatedJobs() throws URISyntaxException {
		List<GoldenJob> jobs = GoldenSet.load(examples());

		assertThat(jobs).extracting(GoldenJob::id).containsExactly("exemplo-1", "exemplo-2", "exemplo-3");
		assertThat(jobs.getFirst().expected().requiredSkills()).containsExactly("Java", "Spring Boot", "Kafka");
		assertThat(jobs.getFirst().toView().description()).contains("Kafka");
	}

	@Test
	void measuresPrecisionAndRecallOfTheListsAndAccuracyOfTheFields() throws URISyntaxException {
		List<GoldenJob> jobs = GoldenSet.load(examples());
		Map<String, JobRequirements> extracted = Map.of(
				// Acertou Java e Spring Boot, perdeu Kafka e inventou Docker; acertou o resto.
				"exemplo-1", requirements("SENIOR", List.of("java", "Spring Boot", "Docker"), List.of("AWS"), "REMOTO", "CLT"),
				// Tudo certo, menos a senioridade.
				"exemplo-2", requirements("PLENO", List.of("Python", "SQL"), List.of(), "HIBRIDO", "PJ"));

		GoldenSet.Score score = GoldenSet.score(jobs, extracted);

		// Obrigatórias: 4 acertos de 5 extraídas e de 5 esperadas, só nas vagas extraídas.
		assertThat(score.jobs()).isEqualTo(2);
		assertThat(score.requiredPrecision()).isCloseTo(4 / 5.0, within(0.001));
		assertThat(score.requiredRecall()).isCloseTo(4 / 5.0, within(0.001));
		assertThat(score.nicePrecision()).isEqualTo(1.0);
		assertThat(score.accuracy()).containsEntry("seniority", 0.5)
			.containsEntry("workModel", 1.0)
			.containsEntry("contractType", 1.0);
		assertThat(score.describe("teste")).contains("obrigatórias: precisão 80%, recall 80%");
	}

	private static JobRequirements requirements(String seniority, List<String> required, List<String> nice,
			String workModel, String contract) {
		return new JobRequirements(seniority, required, nice, workModel, null, "CONTRATANTE", List.of(), contract);
	}

	private static Path examples() throws URISyntaxException {
		return Path.of(GoldenSetTest.class.getResource("/fixtures/golden").toURI());
	}

}
