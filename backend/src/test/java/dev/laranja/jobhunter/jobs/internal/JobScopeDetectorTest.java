package dev.laranja.jobhunter.jobs.internal;

import dev.laranja.jobhunter.jobs.JobScope;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class JobScopeDetectorTest {

	@Test
	void englishDescriptionIsInternational() {
		assertThat(JobScopeDetector.detect("""
				We are looking for a Senior Backend Engineer to join our Payments team. You will design and build
				services in Java and Kotlin that handle millions of transactions. Our stack runs on AWS and Kafka,
				and we value ownership and clear communication with the rest of the company.
				""")).isEqualTo(JobScope.INTERNACIONAL);
	}

	@Test
	void portugueseDescriptionWithEnglishTechTermsIsNational() {
		assertThat(JobScopeDetector.detect("""
				Buscamos uma pessoa desenvolvedora backend para o time de Payments. Você vai trabalhar com Java,
				Spring Boot, Kafka e AWS, com foco em clean code, code review e design de microservices. Nossa
				empresa é remote first e oferece plano de saúde, vale-refeição e stock options.
				""")).isEqualTo(JobScope.NACIONAL);
	}

	@Test
	void bilingualDescriptionStaysNational() {
		assertThat(JobScopeDetector.detect("""
				Sobre a vaga: buscamos uma pessoa para o time de dados, com experiência em Python e SQL e que goste
				de trabalhar com produto. About the role: we are looking for a data engineer who will work with our
				product team on pipelines and dashboards.
				""")).isEqualTo(JobScope.NACIONAL);
	}

	@ParameterizedTest
	@NullAndEmptySource
	@ValueSource(strings = { "Senior Software Engineer", "Java, Spring Boot, AWS, Kubernetes" })
	void tooLittleTextIsNational(String description) {
		assertThat(JobScopeDetector.detect(description)).isEqualTo(JobScope.NACIONAL);
	}

}
