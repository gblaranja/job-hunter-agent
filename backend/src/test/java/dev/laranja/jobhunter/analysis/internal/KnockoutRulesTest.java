package dev.laranja.jobhunter.analysis.internal;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import dev.laranja.jobhunter.analysis.JobKnockout;
import dev.laranja.jobhunter.analysis.KnockoutStatus;
import dev.laranja.jobhunter.profile.CandidateProfileView;
import dev.laranja.jobhunter.profile.EnglishLevel;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class KnockoutRulesTest {

	@Test
	void readsYearsEnglishResidenceAndContract() {
		String description = """
				Requisitos: no mínimo 8 anos de experiência com Java. Inglês fluente.
				Necessário residir em Campinas ou região, 3x por semana no escritório.
				Diferenciais: inglês avançado é um plus, 10 anos de experiência com Kotlin.
				""";

		List<KnockoutRequirement> found = KnockoutRules.extract(description, "PJ", "HIBRIDO", "Campinas, SP");

		assertThat(found).extracting(k -> k.type() + "=" + k.value())
			.containsExactly("ANOS_EXPERIENCIA=8", "IDIOMA=INGLES:FLUENTE", "LOCALIDADE=Campinas", "PRESENCIAL=3",
					"CONTRATO=PJ");
		assertThat(found.get(0).text()).isEqualTo("no mínimo 8 anos de experiência");
	}

	@Test
	void fullyOnSiteAndEnglishWrittenJobs() {
		assertThat(KnockoutRules.extract("Atuação 100% presencial em São Paulo.", "AMBOS", "NAO_INFORMADO", null))
			.extracting(k -> k.type() + "=" + k.value())
			.containsExactly("PRESENCIAL=5");
		String english = "We are looking for a backend engineer. You will work with our team and the product. "
				+ "We value ownership and you are expected to work with Java and Kafka. Our stack and our culture.";
		assertThat(KnockoutRules.extract(english, "NAO_INFORMADO", "REMOTO", null))
			.extracting(k -> k.type() + "=" + k.value())
			.containsExactly("IDIOMA=INGLES:AVANCADO");
	}

	@Test
	void readsTheSalaryRange() {
		var salary = KnockoutRules.salary("Faixa salarial: R$ 12.000,00 a R$ 15.500 por mês (PJ). VR de R$ 900.", "AMBOS");

		assertThat(salary).isNotNull();
		assertThat(salary.min()).isEqualByComparingTo(new BigDecimal("12000"));
		assertThat(salary.max()).isEqualByComparingTo(new BigDecimal("15500"));
		assertThat(salary.currency()).isEqualTo("BRL");
		assertThat(salary.period()).isEqualTo("MENSAL");
		assertThat(salary.regime()).isEqualTo("PJ");
		assertThat(KnockoutRules.salary("Salary: USD 120k per year", "NAO_INFORMADO").period()).isEqualTo("ANUAL");
		assertThat(KnockoutRules.salary("Benefícios: VR R$ 900 e plano de saúde.", "CLT")).isNull();
	}

	@Test
	void comparesWithTheProfile() {
		var profile = new CandidateProfileView(6, EnglishLevel.AVANCADO, "Ribeirão Preto", "SP", 2, List.of("CLT"),
				List.of("BR"), null, null, List.of(), null, null, 12, null, null, null, Instant.now());
		var requirements = List.of(new KnockoutRequirement("ANOS_EXPERIENCIA", "8 anos", "8"),
				new KnockoutRequirement("IDIOMA", "inglês fluente", "INGLES:FLUENTE"),
				new KnockoutRequirement("LOCALIDADE", "residir em Ribeirão Preto", "Ribeirão Preto"),
				new KnockoutRequirement("LOCALIDADE", "São Paulo", "São Paulo"),
				new KnockoutRequirement("PRESENCIAL", "2x por semana", "2"),
				new KnockoutRequirement("CONTRATO", "PJ", "PJ"), new KnockoutRequirement("AUTORIZACAO_TRABALHO", "US", "USA"),
				new KnockoutRequirement("INVALIDO", "x", "y"));

		List<JobKnockout> result = KnockoutEvaluator.evaluate(requirements, profile);

		assertThat(result).extracting(JobKnockout::status)
			.containsExactly(KnockoutStatus.NAO_ATENDE, KnockoutStatus.NAO_ATENDE, KnockoutStatus.ATENDE,
					KnockoutStatus.SEM_INFORMACAO, KnockoutStatus.ATENDE, KnockoutStatus.NAO_ATENDE,
					KnockoutStatus.NAO_ATENDE);
		assertThat(result.get(0).reason()).isEqualTo("Você tem 6 anos; a vaga pede 8.");
		assertThat(KnockoutEvaluator.evaluate(requirements, CandidateProfileView.empty()))
			.allMatch(knockout -> knockout.status() == KnockoutStatus.SEM_INFORMACAO);
	}

}
