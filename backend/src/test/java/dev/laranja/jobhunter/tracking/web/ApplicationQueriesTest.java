package dev.laranja.jobhunter.tracking.web;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ApplicationQueriesTest {

	@Test
	void excerptShowsTheTermInContext() {
		String description = "Buscamos pessoa desenvolvedora backend. " + "x".repeat(200)
				+ " Experiência com Apache Kafka e mensageria. " + "y".repeat(200);

		String excerpt = ApplicationQueries.excerpt(description, "kafka");

		assertThat(excerpt).contains("Apache Kafka").startsWith("…").endsWith("…").hasSizeLessThan(200);
	}

	@Test
	void noExcerptWithoutSearchOrMatch() {
		assertThat(ApplicationQueries.excerpt("Java e Spring", null)).isNull();
		assertThat(ApplicationQueries.excerpt("Java e Spring", "golang")).isNull();
		assertThat(ApplicationQueries.excerpt(null, "java")).isNull();
	}

}
