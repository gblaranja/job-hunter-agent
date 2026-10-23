package dev.laranja.jobhunter.jobs.internal;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

import dev.laranja.jobhunter.shared.TextNormalizer;

/**
 * Identidade de uma vaga independente da fonte: sha256 de empresa, título e localidade normalizados. A mesma
 * vaga vinda de dois alertas diferentes gera o mesmo fingerprint.
 */
public final class JobFingerprint {

	private JobFingerprint() {
	}

	public static String of(String company, String title, String location) {
		String identity = String.join("|", TextNormalizer.normalize(company), TextNormalizer.normalize(title),
				TextNormalizer.normalize(location));
		try {
			byte[] digest = MessageDigest.getInstance("SHA-256").digest(identity.getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(digest);
		}
		catch (NoSuchAlgorithmException ex) {
			throw new IllegalStateException("SHA-256 indisponível na JVM", ex);
		}
	}

}
