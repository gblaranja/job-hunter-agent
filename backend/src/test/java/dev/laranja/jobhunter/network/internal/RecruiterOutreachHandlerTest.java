package dev.laranja.jobhunter.network.internal;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RecruiterOutreachHandlerTest {

	@Test
	void namesTheRecruiterFromTheAddressWhenTheEmailDoesNotSign() {
		assertThat(RecruiterOutreachHandler.nameFromAddress("maria.silva@agencia.com")).isEqualTo("Maria Silva");
		assertThat(RecruiterOutreachHandler.nameFromAddress("joao_souza123@x.com")).isEqualTo("Joao Souza123");
		assertThat(RecruiterOutreachHandler.nameFromAddress(null)).isEqualTo("Recrutador(a)");
	}

}
