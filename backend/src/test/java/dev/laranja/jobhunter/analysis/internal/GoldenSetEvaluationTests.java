package dev.laranja.jobhunter.analysis.internal;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import dev.laranja.jobhunter.IntegrationTest;
import dev.laranja.jobhunter.analysis.internal.GoldenSet.GoldenJob;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Mede a extração de requisitos contra o conjunto anotado em {@code JOBHUNTER_GOLDEN_DIR} (fora do git). Fica fora
 * do CI: roda só com a variável definida, por exemplo antes e depois de mudar o prompt de extração.
 *
 * <pre>
 * JOBHUNTER_GOLDEN_DIR=~/job-hunter-golden JOBHUNTER_AI_API_KEY=... ./mvnw test -Dtest=GoldenSetEvaluationTests
 * </pre>
 *
 * Sem a chave da Claude API, mede só a extração determinística (dicionário e regras).
 */
@EnabledIfEnvironmentVariable(named = "JOBHUNTER_GOLDEN_DIR", matches = ".+")
class GoldenSetEvaluationTests extends IntegrationTest {

	@Autowired
	private RequirementsExtractor extractor;

	@Test
	void reportsTheExtractionQuality() {
		List<GoldenJob> jobs = GoldenSet.load(Path.of(System.getenv("JOBHUNTER_GOLDEN_DIR")));
		assertThat(jobs).as("vagas anotadas em JOBHUNTER_GOLDEN_DIR").isNotEmpty();

		Map<String, JobRequirements> deterministic = new LinkedHashMap<>();
		jobs.forEach(job -> deterministic.put(job.id(), this.extractor.deterministic(job.toView())));
		System.out.println(GoldenSet.score(jobs, deterministic).describe("Determinístico"));

		Map<String, JobRequirements> withLlm = new LinkedHashMap<>();
		jobs.stream()
			.filter(job -> this.extractor.canUseLlm(job.toView()))
			.forEach(job -> withLlm.put(job.id(), this.extractor.withLlm(job.toView())));
		if (!withLlm.isEmpty()) {
			System.out.println(GoldenSet.score(jobs, withLlm).describe(this.extractor.extractionVersion()));
		}
	}

}
