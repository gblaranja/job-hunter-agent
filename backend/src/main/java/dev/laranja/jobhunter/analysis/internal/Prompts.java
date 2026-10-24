package dev.laranja.jobhunter.analysis.internal;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

import org.springframework.core.io.ClassPathResource;

/**
 * Prompts versionados em {@code resources/prompts}. A versão entra no {@code model_version} da análise, para
 * reprocessar as vagas quando um prompt mudar.
 */
enum Prompts {

	EXTRACT_REQUIREMENTS("extract-requirements", 1), MATCH_SCORE("match-score", 1), RESUME_SKILLS("resume-skills", 2);

	private final String name;

	private final int version;

	private final String text;

	Prompts(String name, int version) {
		this.name = name;
		this.version = version;
		try {
			this.text = new ClassPathResource("prompts/" + name + ".v" + version + ".md")
				.getContentAsString(StandardCharsets.UTF_8)
				.strip();
		}
		catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
	}

	String text() {
		return this.text;
	}

	String id() {
		return this.name + "@v" + this.version;
	}

}
