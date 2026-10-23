package dev.laranja.jobhunter.companies;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class CompanyNameNormalizerTest {

	@ParameterizedTest
	@CsvSource(delimiter = '|', textBlock = """
			Itaú Unibanco S.A.          | itau unibanco
			ITAU UNIBANCO               | itau unibanco
			Banco Exemplo S/A           | banco exemplo
			Consultoria Tech Ltda.      | consultoria tech
			Stone Pagamentos do Brasil  | stone pagamentos
			Nubank                      | nubank
			Brasil                      | brasil
			""")
	void removesLegalSuffixesAccentsAndCase(String name, String expected) {
		assertThat(CompanyNameNormalizer.normalize(name)).isEqualTo(expected);
	}

}
