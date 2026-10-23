package dev.laranja.jobhunter.resumes.internal;

import dev.laranja.jobhunter.TestFiles;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ResumeTextExtractorTest {

	private final ResumeTextExtractor extractor = new ResumeTextExtractor();

	@Test
	void extractsTextFromAPdf() {
		assertThat(this.extractor.extract(TestFiles.pdfWithText("Desenvolvedor Java com Spring Boot")))
			.contains("Desenvolvedor Java com Spring Boot");
	}

	@Test
	void returnsNullWhenThereIsNoReadableText() {
		assertThat(this.extractor.extract(TestFiles.pdf("sem-texto"))).isNull();
	}

}
