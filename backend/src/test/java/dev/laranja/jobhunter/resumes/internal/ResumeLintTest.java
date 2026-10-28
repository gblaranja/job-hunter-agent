package dev.laranja.jobhunter.resumes.internal;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

import dev.laranja.jobhunter.resumes.ResumeLintWarning;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ResumeLintTest {

	private static final String GOOD = """
			Ana Lima
			ana.lima@example.com · (16) 99123-4567 · Ribeirão Preto/SP

			Resumo
			Desenvolvedora backend com 8 anos em Java, Spring Boot e Kafka, com foco em sistemas de crédito.

			Experiência
			Banco Azul — Desenvolvedora Sênior, 03/2021 - atual
			Construção de APIs de crédito em Java 21 e Spring Boot, eventos em Kafka e deploy em Kubernetes.
			Loja Verde — Desenvolvedora, 2016 a 2021
			Microsserviços de pagamento, integração com gateways e observabilidade com Datadog.

			Formação
			Ciência da Computação, Universidade Federal, 2011 a 2015

			Habilidades
			Java, Spring Boot, Kafka, PostgreSQL, AWS, Docker, Kubernetes
			""";

	@Test
	void aResumeTheAtsReadsWellHasNoWarnings() {
		assertThat(ResumeLint.check(GOOD, 1)).isEmpty();
	}

	@Test
	void pointsOutMissingContactSectionsAndOrder() {
		String text = """
				Ana Lima
				Experiência
				Loja Verde, 2016 a 2019
				Banco Azul, 2019 - atual
				""";

		assertThat(ResumeLint.check(text, 2)).extracting(ResumeLintWarning::code)
			.containsExactly("SEM_EMAIL", "SEM_TELEFONE", "SECAO_AUSENTE", "SECAO_AUSENTE", "DATAS_FORA_DE_ORDEM",
					"TEXTO_CURTO");
		assertThat(ResumeLint.check(null, 1)).extracting(ResumeLintWarning::code).containsExactly("SEM_TEXTO");
	}

	@Test
	void aTwoColumnPdfShowsTheWarning() throws IOException {
		List<String> left = List.of("Ana Lima", "Contato", "ana@example.com", "(16) 99123-4567", "Habilidades", "Java",
				"Spring Boot", "Kafka", "AWS", "Docker", "Idiomas", "Inglês", "Formação", "Computação", "2011 a 2015");
		List<String> right = List.of("Experiência", "Banco Azul", "2021 - atual", "APIs de crédito", "Loja Verde",
				"2016 a 2021", "Pagamentos", "Projetos", "Motor de regras", "Open Finance", "Cursos", "Kubernetes",
				"Terraform", "Observabilidade", "Datadog");

		String text = new ResumeTextExtractor().extract(twoColumns(left, right));

		assertThat(ResumeLint.check(text, 1)).extracting(ResumeLintWarning::code).contains("DUAS_COLUNAS", "TEXTO_CURTO");
	}

	private static byte[] twoColumns(List<String> left, List<String> right) throws IOException {
		try (PDDocument document = new PDDocument(); var bytes = new ByteArrayOutputStream()) {
			PDPage page = new PDPage();
			document.addPage(page);
			var font = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
			try (var content = new PDPageContentStream(document, page)) {
				for (int column = 0; column < 2; column++) {
					List<String> lines = (column == 0) ? left : right;
					for (int i = 0; i < lines.size(); i++) {
						content.beginText();
						content.setFont(font, 10);
						content.newLineAtOffset(column == 0 ? 50 : 320, 740 - i * 18);
						content.showText(lines.get(i).replace("ê", "e").replace("ç", "c").replace("ã", "a"));
						content.endText();
					}
				}
			}
			document.save(bytes);
			return bytes.toByteArray();
		}
	}

}
