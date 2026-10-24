package dev.laranja.jobhunter.jobs.internal;

import dev.laranja.jobhunter.jobs.JobScope;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class JobScopeDetectorTest {

	private static final String ENGLISH = """
			We are looking for a Senior Backend Engineer to join our Payments team. You will design and build
			services in Java and Kotlin that handle millions of transactions. Our stack runs on AWS and Kafka,
			and we value ownership and clear communication with the rest of the company.
			""";

	private static final String PORTUGUESE = """
			Buscamos uma pessoa desenvolvedora backend para o time de Payments. Você vai trabalhar com Java,
			Spring Boot, Kafka e AWS, com foco em clean code, code review e design de microservices. Nossa
			empresa é remote first e oferece plano de saúde, vale-refeição e stock options.
			""";

	@Test
	void englishDescriptionIsInternational() {
		assertThat(JobScopeDetector.detect("Desenvolvedor Java", "Brasil (Remoto)", ENGLISH))
			.isEqualTo(JobScope.INTERNACIONAL);
	}

	@Test
	void portugueseDescriptionWithEnglishTitleAndTechTermsIsNational() {
		assertThat(JobScopeDetector.detect("Senior Backend Engineer", "São Paulo, SP (Remoto)", PORTUGUESE))
			.isEqualTo(JobScope.NACIONAL);
	}

	@Test
	void bilingualDescriptionStaysNational() {
		assertThat(JobScopeDetector.detect(null, null, """
				Sobre a vaga: buscamos uma pessoa para o time de dados, com experiência em Python e SQL e que goste
				de trabalhar com produto. About the role: we are looking for a data engineer who will work with our
				product team on pipelines and dashboards.
				""")).isEqualTo(JobScope.NACIONAL);
	}

	// Alertas do LinkedIn: sem descrição, decidem a localidade e o título.
	@ParameterizedTest
	@CsvSource(delimiter = '|', textBlock = """
			Senior Java Developer, Brazil                       | Brasil (Remoto)              | INTERNACIONAL
			Java Software Engineer - Remote Work                | Brasil (Remoto)              | INTERNACIONAL
			AI Integration Engineer (Remote, Rio de Janeiro)    | Brasil (Remoto)              | INTERNACIONAL
			Applied AI Engineer                                 | Montenegro, RS (Remoto)      | INTERNACIONAL
			DEV Back-End (Java - Spring)                        | Santiago e Região (Híbrido)  | INTERNACIONAL
			Desenvolvedor Java Sênior                           | Colômbia (Remoto)            | INTERNACIONAL
			Software Engineer - Java - CF Benchmark             | Chile (Remoto)               | INTERNACIONAL
			Senior Software Engineer, Build Loop                | Buenos Aires e Região (Remoto) | INTERNACIONAL
			Senior Java Developer, Colombia                     | Medellin (Remoto)            | INTERNACIONAL
			Desenvolvedor Java Sênior                           | Brasil (Remoto)              | NACIONAL
			SOFTWARE ENGINEER SÊNIOR - PLATAFORMAS IFOOD PAGO   | Brasil (Remoto)              | NACIONAL
			Back-end Engineer Pleno - Assessoria Humana         | Brasil (Remoto)              | NACIONAL
			Staff Software Engineer (Remoto) - 5594             | Brasil (Remoto)              | NACIONAL
			Sr Integration Developer - (Java e Spring boot)     | Brasil (Remoto)              | NACIONAL
			[Remoto] Desenvolvedor(a) Backend Sênior / Backend Engineer | São Paulo, SP (Remoto) | NACIONAL
			DEV - BOLSA FAPEMIG - COME TO MINAS                 | Belo Horizonte, MG           | NACIONAL
			Desenvolvedor Back End Pleno                        | Porto Alegre, RS (Remoto)    | NACIONAL
			Analista de Desenvolvimento                         | São Paulo e Região (Remoto)  | NACIONAL
			""")
	void withoutDescriptionUsesLocationAndTitle(String title, String location, JobScope expected) {
		assertThat(JobScopeDetector.detect(title, location, null)).isEqualTo(expected);
	}

	@ParameterizedTest
	@NullAndEmptySource
	@ValueSource(strings = { "Java, Spring Boot, AWS, Kubernetes", "Vaga para Java" })
	void tooLittleToTellIsNational(String text) {
		assertThat(JobScopeDetector.detect(text, text, text)).isEqualTo(JobScope.NACIONAL);
	}

}
