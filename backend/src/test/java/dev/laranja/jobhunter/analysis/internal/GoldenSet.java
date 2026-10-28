package dev.laranja.jobhunter.analysis.internal;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import dev.laranja.jobhunter.jobs.JobOrigin;
import dev.laranja.jobhunter.jobs.JobScope;
import dev.laranja.jobhunter.jobs.JobView;
import dev.laranja.jobhunter.jobs.WorkModel;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.json.JsonMapper;

/**
 * Conjunto de vagas anotadas à mão para medir a extração de requisitos. Cada arquivo {@code *.json} traz a vaga e o
 * que a extração deveria devolver:
 *
 * <pre>
 * {"id": "banco-1", "title": "...", "company": "...", "location": "...", "workModel": "REMOTO",
 *  "description": "...",
 *  "expected": {"seniority": "SENIOR", "requiredSkills": ["Java", "Kafka"], "niceToHave": ["AWS"],
 *               "workModel": "REMOTO", "contractType": "CLT"}}
 * </pre>
 *
 * As vagas reais ficam fora do git (texto de terceiros), num diretório apontado por {@code JOBHUNTER_GOLDEN_DIR}.
 */
final class GoldenSet {

	private GoldenSet() {
	}

	static List<GoldenJob> load(Path directory) {
		JsonMapper json = JsonMapper.builder().build();
		try (Stream<Path> files = Files.list(directory)) {
			return files.filter(file -> file.toString().endsWith(".json"))
				.sorted()
				.map(file -> json.readValue(file.toFile(), GoldenJob.class))
				.toList();
		}
		catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
	}

	/** Compara as extrações com o esperado: precisão e recall nas listas, acerto nos campos simples. */
	static Score score(List<GoldenJob> jobs, Map<String, JobRequirements> extracted) {
		Counts required = new Counts();
		Counts nice = new Counts();
		Map<String, int[]> exact = new LinkedHashMap<>();
		for (GoldenJob job : jobs) {
			JobRequirements actual = extracted.get(job.id());
			if (actual == null) {
				continue;
			}
			required.add(job.expected().requiredSkills(), actual.requiredSkills());
			nice.add(job.expected().niceToHave(), actual.niceToHave());
			hit(exact, "seniority", job.expected().seniority(), actual.seniority());
			hit(exact, "workModel", job.expected().workModel(), actual.workModel());
			hit(exact, "contractType", job.expected().contractType(), actual.contractType());
		}
		Map<String, Double> accuracy = new LinkedHashMap<>();
		exact.forEach((field, counts) -> accuracy.put(field, (counts[1] == 0) ? 1.0 : (double) counts[0] / counts[1]));
		return new Score(extracted.size(), required.precision(), required.recall(), nice.precision(), nice.recall(),
				accuracy);
	}

	private static void hit(Map<String, int[]> exact, String field, @Nullable String expected, @Nullable String actual) {
		int[] counts = exact.computeIfAbsent(field, key -> new int[2]);
		counts[1]++;
		if (Objects.equals(blankToNull(expected), blankToNull(actual))) {
			counts[0]++;
		}
	}

	private static @Nullable String blankToNull(@Nullable String value) {
		return (value == null || value.isBlank()) ? null : value.strip();
	}

	record GoldenJob(String id, String title, String company, @Nullable String location, @Nullable String workModel,
			String description, Expected expected) {

		JobView toView() {
			WorkModel model = (this.workModel == null) ? WorkModel.NAO_INFORMADO : WorkModel.valueOf(this.workModel);
			return new JobView(0, JobOrigin.MANUAL, this.title, this.company, this.location, model, JobScope.NACIONAL,
					null, null, this.description, Instant.EPOCH);
		}

	}

	record Expected(@Nullable String seniority, List<String> requiredSkills, List<String> niceToHave,
			@Nullable String workModel, @Nullable String contractType) {
	}

	/**
	 * @param accuracy fração de acerto de cada campo simples (senioridade, modelo de trabalho, regime)
	 */
	record Score(int jobs, double requiredPrecision, double requiredRecall, double nicePrecision, double niceRecall,
			Map<String, Double> accuracy) {

		String describe(String label) {
			List<String> lines = new ArrayList<>();
			lines.add("%s: %d vagas".formatted(label, this.jobs));
			lines.add("  obrigatórias: precisão %.0f%%, recall %.0f%%".formatted(this.requiredPrecision * 100,
					this.requiredRecall * 100));
			lines.add("  desejáveis:   precisão %.0f%%, recall %.0f%%".formatted(this.nicePrecision * 100,
					this.niceRecall * 100));
			this.accuracy.forEach((field, value) -> lines.add("  %s: %.0f%% de acerto".formatted(field, value * 100)));
			return String.join("\n", lines);
		}

	}

	/** Acertos, extraídas e esperadas, comparando as skills sem diferença de caixa. */
	private static final class Counts {

		private int truePositives;

		private int extracted;

		private int expected;

		void add(List<String> expectedSkills, List<String> actualSkills) {
			Set<String> want = lower(expectedSkills);
			Set<String> got = lower(actualSkills);
			this.expected += want.size();
			this.extracted += got.size();
			this.truePositives += (int) got.stream().filter(want::contains).count();
		}

		double precision() {
			return (this.extracted == 0) ? 1.0 : (double) this.truePositives / this.extracted;
		}

		double recall() {
			return (this.expected == 0) ? 1.0 : (double) this.truePositives / this.expected;
		}

		private static Set<String> lower(List<String> skills) {
			return skills.stream().map(skill -> skill.strip().toLowerCase(Locale.ROOT)).collect(Collectors.toSet());
		}

	}

}
