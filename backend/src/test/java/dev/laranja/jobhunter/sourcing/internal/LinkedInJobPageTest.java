package dev.laranja.jobhunter.sourcing.internal;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LinkedInJobPageTest {

	static final String PAGE = """
			<html><body>
			<h2 class="top-card-layout__title">Senior Java Developer</h2>
			<div class="description__text description__text--rich">
			  <section class="show-more-less-html">
			    <div class="show-more-less-html__markup">
			      <p>We are looking for a <strong>Senior Java Developer</strong> to join our team.</p>
			      <ul><li>Java 21 and Spring Boot</li><li>Kafka and AWS</li></ul>
			      <p>You will work with our product team across the Americas.</p>
			    </div>
			  </section>
			</div>
			</body></html>
			""";

	@Test
	void readsTheDescriptionAsText() {
		assertThat(LinkedInJobPage.description(PAGE)).startsWith("We are looking for a Senior Java Developer")
			.contains("- Java 21 and Spring Boot")
			.contains("- Kafka and AWS")
			.doesNotContain("<p>")
			.doesNotContain("top-card");
	}

	@Test
	void pageWithoutDescriptionGivesNothing() {
		assertThat(LinkedInJobPage.description("<html><body><h1>Entre no LinkedIn</h1></body></html>")).isNull();
		assertThat(LinkedInJobPage.description("""
				<div class="show-more-less-html__markup"><p>Vaga Java.</p></div>
				""")).isNull();
		assertThat(LinkedInJobPage.description(null)).isNull();
	}

}
