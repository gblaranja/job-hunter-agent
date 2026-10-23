package dev.laranja.jobhunter.shared.security;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.security.crypto.encrypt.Encryptors;
import org.springframework.security.crypto.encrypt.BytesEncryptor;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

/**
 * Cifra segredos guardados no banco (os tokens OAuth do Gmail) com AES-GCM de 256 bits; cada valor usa um IV
 * aleatório. A chave e o salt vêm do ambiente e nunca vão para o repositório.
 */
@Component
public class SecretCipher {

	private final BytesEncryptor encryptor;

	SecretCipher(EncryptionProperties properties) {
		this.encryptor = Encryptors.stronger(properties.key(), properties.salt());
	}

	public String encrypt(String plainText) {
		return Base64.getEncoder().encodeToString(this.encryptor.encrypt(plainText.getBytes(StandardCharsets.UTF_8)));
	}

	public String decrypt(String cipherText) {
		return new String(this.encryptor.decrypt(Base64.getDecoder().decode(cipherText)), StandardCharsets.UTF_8);
	}

	/**
	 * @param key senha da qual a chave AES é derivada (PBKDF2)
	 * @param salt salt em hexadecimal, com pelo menos 8 bytes
	 */
	@Validated
	@ConfigurationProperties("jobhunter.encryption")
	record EncryptionProperties(@NotBlank String key,
			@NotBlank @Pattern(regexp = "([0-9a-fA-F]{2}){8,}", message = "deve ser hexadecimal com pelo menos 16 dígitos") String salt) {
	}

}
