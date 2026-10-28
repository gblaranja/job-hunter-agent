package dev.laranja.jobhunter.analysis.internal;

import java.time.Year;

import dev.laranja.jobhunter.profile.EnglishLevel;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ResumeHintsTest {

	@Test
	void yearsCountFromTheFirstPeriod() {
		String resume = """
				Experiência
				Banco Azul — Desenvolvedor Sênior, 03/2021 - atual
				Loja Verde — Desenvolvedor, jan 2016 a dez 2020
				Formação: Bacharelado, 2011 – 2015
				""";
		// a formação também é um período: a regra é aproximada, por isso vira só sugestão
		assertThat(ResumeHints.yearsOfExperience(resume, Year.of(2026))).hasValue(15);
		assertThat(ResumeHints.yearsOfExperience("Banco Azul 2019 - presente", Year.of(2026))).hasValue(7);
		assertThat(ResumeHints.yearsOfExperience("Sem datas", Year.of(2026))).isEmpty();
	}

	@Test
	void englishTakesTheHighestLevelMentioned() {
		assertThat(ResumeHints.englishLevel("Idiomas: Inglês avançado, espanhol básico")).hasValue(EnglishLevel.AVANCADO);
		assertThat(ResumeHints.englishLevel("Fluent English")).hasValue(EnglishLevel.FLUENTE);
		assertThat(ResumeHints.englishLevel("Inglês: intermediário (B2)")).hasValue(EnglishLevel.INTERMEDIARIO);
		assertThat(ResumeHints.englishLevel("Java, Spring")).isEmpty();
		assertThat(ResumeHints.parseEnglish("avançado")).hasValue(EnglishLevel.AVANCADO);
		assertThat(ResumeHints.parseEnglish("qualquer")).isEmpty();
	}

}
