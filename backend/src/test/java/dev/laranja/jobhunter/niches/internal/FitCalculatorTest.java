package dev.laranja.jobhunter.niches.internal;

import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

import dev.laranja.jobhunter.niches.FitBand;
import dev.laranja.jobhunter.niches.internal.FitCalculator.Inputs;
import dev.laranja.jobhunter.niches.internal.FitCalculator.JobText;
import dev.laranja.jobhunter.niches.internal.FitCalculator.Result;
import dev.laranja.jobhunter.niches.internal.FitCalculator.Weights;
import dev.laranja.jobhunter.recognition.RecognitionLevel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static org.assertj.core.api.Assertions.assertThat;

class FitCalculatorTest {

	private static final Weights DEFAULT = new Weights(0.35, 0.30, 0.20, 0.15);

	private static final List<String> DOMAIN = List.of("crédito", "antifraude", "KYC", "Open Finance", "Pix");

	private static final JobText JAVA_CREDIT = JobText.of("Desenvolvedor Java Spring Boot para o motor de crédito, Kafka e AWS", true);

	private static final JobText JAVA = JobText.of("Desenvolvedor Backend Java Sênior", true);

	private static final JobText NODE_ONSITE = JobText.of("Desenvolvedora Node.js presencial em Recife", false);

	static Stream<Arguments> cases() {
		return Stream.of(
				// banco importado do Banco Central, sem vagas: stack e domínio neutros
				Arguments.of("banco sem vagas", inputs("Bancos digitais e múltiplos", 85, null, 0, List.of()), 50, FitBand.MEDIO),
				// fintech de crédito com vagas Java no domínio, remota e num ranking GPTW
				Arguments.of("fintech de crédito aderente", inputs("Crédito", 90, RecognitionLevel.RANKING, 4,
						Collections.nCopies(10, JAVA_CREDIT)), 95, FitBand.ALTO),
				// seguradora com vagas de outra stack, presenciais
				Arguments.of("seguradora fora da stack", inputs("Seguros e consórcios", 40, null, 1,
						Collections.nCopies(5, NODE_ONSITE)), 16, FitBand.BAIXO),
				// metade das vagas em Java, sem domínio
				Arguments.of("pagamentos com metade Java", inputs("Pagamentos e adquirência", 65, RecognitionLevel.CERTIFICADA, 3,
						List.of(JAVA, JAVA, NODE_ONSITE, NODE_ONSITE)), 47, FitBand.MEDIO));
	}

	@ParameterizedTest(name = "{0}")
	@MethodSource("cases")
	void computesTheWeightedFitAndTheBand(String name, Inputs inputs, int score, FitBand band) {
		Result result = FitCalculator.compute(inputs, DEFAULT, DOMAIN);

		assertThat(result.score()).isEqualTo(score);
		assertThat(result.band()).isEqualTo(band);
	}

	@Test
	void reasonsExplainEachComponent() {
		Result result = FitCalculator.compute(inputs("Crédito", 90, RecognitionLevel.CERTIFICADA, 3,
				List.of(JAVA_CREDIT, JAVA_CREDIT, JAVA, NODE_ONSITE)), DEFAULT, DOMAIN);

		assertThat(result.reasons()).containsExactly("Crédito", "3 das 4 últimas vagas pedem Java/Spring",
				"Domínio nas vagas: crédito", "Certificada GPTW 2025", "3 vagas nos últimos 90 dias",
				"Remoto, híbrido ou em Ribeirão Preto em 3 de 4 vagas");
		assertThat(result.components()).containsEntry("stack", 70).containsEntry("domain", 50).containsEntry("signals", 80);
	}

	@Test
	void consultanciesWithFinancialClientsGetABonus() {
		Inputs plain = new Inputs("Alocação e outsourcing", 70, true, List.of("Varejo"), null, null, 1, List.of(JAVA));
		Inputs financial = new Inputs("Alocação e outsourcing", 70, true, List.of("Bancos", "Varejo"), null, null, 1, List.of(JAVA));

		Result withoutBonus = FitCalculator.compute(plain, DEFAULT, DOMAIN);
		Result withBonus = FitCalculator.compute(financial, DEFAULT, DOMAIN);

		assertThat(withBonus.score()).isEqualTo(withoutBonus.score() + FitCalculator.FINANCIAL_CLIENTS_BONUS);
		assertThat(withBonus.reasons()).contains("Aloca em clientes do setor financeiro");
	}

	@Test
	void weightsAreNormalized() {
		Inputs inputs = inputs("Crédito", 90, null, 0, List.of());

		assertThat(FitCalculator.compute(inputs, new Weights(35, 30, 20, 15), DOMAIN).score())
			.isEqualTo(FitCalculator.compute(inputs, DEFAULT, DOMAIN).score());
		assertThat(FitCalculator.compute(inputs, new Weights(1, 0, 0, 0), DOMAIN).score()).isEqualTo(90);
	}

	@Test
	void databaseIsNotABank() {
		String text = Keywords.prepare("Experiência com banco de dados relacional e bancos de dados NoSQL");

		assertThat(Keywords.contains(text, "banco")).isFalse();
		assertThat(Keywords.contains(Keywords.prepare("Somos o maior banco digital"), "banco digital")).isTrue();
		assertThat(Keywords.contains(Keywords.prepare("Experiência em JavaScript"), "java")).isFalse();
	}

	private static Inputs inputs(String segment, int baseFit, RecognitionLevel gptw, int last90, List<JobText> jobs) {
		return new Inputs(segment, baseFit, false, List.of(), gptw, (gptw != null) ? 2025 : null, last90, jobs);
	}

}
