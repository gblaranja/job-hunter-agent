package dev.laranja.jobhunter.interviews.internal;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

import org.springframework.core.io.ClassPathResource;

/** Leitura dos prompts versionados das entrevistas. */
final class Prompts {

	private Prompts() {
	}

	static String load(String path) {
		try {
			return new ClassPathResource(path).getContentAsString(StandardCharsets.UTF_8).strip();
		}
		catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
	}

}
