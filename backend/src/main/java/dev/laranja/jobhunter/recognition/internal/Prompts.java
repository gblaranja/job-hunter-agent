package dev.laranja.jobhunter.recognition.internal;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

import org.springframework.core.io.ClassPathResource;

final class Prompts {

	private Prompts() {
	}

	static String load(String name) {
		try {
			return new ClassPathResource("prompts/" + name).getContentAsString(StandardCharsets.UTF_8).strip();
		}
		catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
	}

}
