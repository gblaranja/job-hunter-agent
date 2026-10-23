package dev.laranja.jobhunter.shared.security;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SecretCipherTest {

	private final SecretCipher cipher = new SecretCipher(
			new SecretCipher.EncryptionProperties("uma-chave-de-teste", "5c0744940b5c369b"));

	@Test
	void roundTripsAndUsesARandomIvEachTime() {
		String first = this.cipher.encrypt("refresh-token-123");
		String second = this.cipher.encrypt("refresh-token-123");

		assertThat(first).isNotEqualTo(second).doesNotContain("refresh-token");
		assertThat(this.cipher.decrypt(first)).isEqualTo("refresh-token-123");
	}

	@Test
	void anotherKeyCannotDecrypt() {
		SecretCipher other = new SecretCipher(new SecretCipher.EncryptionProperties("outra-chave", "5c0744940b5c369b"));

		assertThatThrownBy(() -> other.decrypt(this.cipher.encrypt("segredo"))).isInstanceOf(RuntimeException.class);
	}

}
