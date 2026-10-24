package dev.laranja.jobhunter.interviews.internal;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class StoryPolisherTest {

	@Test
	void flagsNumbersThatWereNotInYourText() {
		String original = "Reduzi o tempo de 40 minutos para 5 minutos em 2024.";

		assertThat(StoryPolisher.introducedNumbers(original, "Em 2024, reduzi o tempo de 40 para 5 minutos.")).isEmpty();
		assertThat(StoryPolisher.introducedNumbers(original, "Reduzi 87,5% do tempo, de 40 para 5 minutos.")).containsExactly("87,5");
		assertThat(StoryPolisher.introducedNumbers(original, "Atendi [N clientes] e reduzi de 40 para 5 minutos.")).isEmpty();
	}

}
