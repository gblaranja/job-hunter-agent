package dev.laranja.jobhunter.shared;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class TextNormalizerTest {

	@ParameterizedTest
	@CsvSource(delimiter = '|', textBlock = """
			Desenvolvedor Java Sênior       | desenvolvedor java senior
			  ITAÚ   Unibanco S.A.          | itau unibanco s a
			Back-end (Spring Boot) - Remoto | back end spring boot remoto
			São Paulo/SP                    | sao paulo sp
			""")
	void normalizesCaseAccentsAndPunctuation(String input, String expected) {
		assertThat(TextNormalizer.normalize(input)).isEqualTo(expected);
	}

	@ParameterizedTest
	@CsvSource(nullValues = "NULL", value = { "NULL", "''", "'  --  '" })
	void returnsEmptyForBlankInput(String input) {
		assertThat(TextNormalizer.normalize(input)).isEmpty();
	}

}
