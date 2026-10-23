package dev.laranja.jobhunter.tailoring.internal;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.List;

import dev.laranja.jobhunter.resumes.internal.ResumeFileType;
import dev.laranja.jobhunter.resumes.internal.ResumeFileValidator;
import dev.laranja.jobhunter.resumes.internal.ResumeTextExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MarkdownDocxTest {

	private static final String RESUME = """
			# Maria Souza
			São Paulo · maria_souza@example.com · [LinkedIn](https://linkedin.com/in/maria_souza)

			## Resumo
			Desenvolvedora backend com **Java** e Spring Boot.

			## Experiência
			### Desenvolvedora Sênior · Banco Exemplo
			*2021 – atual · Remoto*
			- Construí APIs REST em Spring Boot que processam [X] pagamentos por dia.
			* Mensageria assíncrona com RabbitMQ\\*.

			---
			""";

	private final MarkdownDocx docx = new MarkdownDocx();

	@Test
	void writesADocxThatTheUploadAcceptsAndTheExtractorReads() {
		byte[] content = this.docx.write(RESUME, "Backend · Banco Exemplo");

		assertThat(new ResumeFileValidator().validate(content)).isEqualTo(ResumeFileType.DOCX);
		assertThat(new ResumeTextExtractor().extract(content)).contains("Maria Souza", "Resumo",
				"Desenvolvedora Sênior · Banco Exemplo", "LinkedIn (https://linkedin.com/in/maria_souza)",
				"maria_souza@example.com", "processam [X] pagamentos", "RabbitMQ*.");
	}

	@Test
	void keepsTheLayoutSimpleForAts() throws IOException {
		try (XWPFDocument document = new XWPFDocument(
				new ByteArrayInputStream(this.docx.write(RESUME, "Backend · Banco Exemplo")))) {
			assertThat(document.getTables()).isEmpty();
			assertThat(document.getAllPictures()).isEmpty();
			List<XWPFParagraph> paragraphs = document.getParagraphs();
			assertThat(paragraphs).extracting(XWPFParagraph::getText)
				.containsExactly("Maria Souza", "São Paulo · maria_souza@example.com · LinkedIn (https://linkedin.com/in/maria_souza)",
						"Resumo", "Desenvolvedora backend com Java e Spring Boot.", "Experiência",
						"Desenvolvedora Sênior · Banco Exemplo",
						"2021 – atual · Remoto", "•\tConstruí APIs REST em Spring Boot que processam [X] pagamentos por dia.",
						"•\tMensageria assíncrona com RabbitMQ*.");
			XWPFRun java = paragraphs.get(3).getRuns().stream().filter(run -> "Java".equals(run.text())).findFirst().orElseThrow();
			assertThat(java.isBold()).isTrue();
			assertThat(paragraphs.get(6).getRuns().getFirst().isItalic()).isTrue();
			assertThat(paragraphs.get(2).getRuns().getFirst().isBold()).isTrue();
		}
	}

}
