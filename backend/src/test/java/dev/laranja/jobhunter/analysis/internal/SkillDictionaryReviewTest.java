package dev.laranja.jobhunter.analysis.internal;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SkillDictionaryReviewTest {

	@Test
	void reviewedEntriesComplementTheFileWithoutOverridingIt() {
		SkillDictionary dictionary = new SkillDictionary(Map.of("Spring Boot", List.of("springboot")));
		assertThat(dictionary.isKnown("Elixir")).isFalse();

		dictionary.replaceReviewed(Map.of("Elixir", List.of("elixir lang"), "Spring Boot 3", List.of("springboot")));

		assertThat(dictionary.isKnown("ELIXIR")).isTrue();
		assertThat(dictionary.canonical("elixir-lang")).isEqualTo("Elixir");
		assertThat(dictionary.detect("Vaga com Elixir e Phoenix")).containsExactly("Elixir");
		// o apelido do arquivo continua apontando para a grafia do arquivo
		assertThat(dictionary.canonical("springboot")).isEqualTo("Spring Boot");

		dictionary.replaceReviewed(Map.of());
		assertThat(dictionary.isKnown("Elixir")).isFalse();
		assertThat(dictionary.isKnown("Spring Boot")).isTrue();
	}

}
