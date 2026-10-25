package dev.laranja.jobhunter.resumes.internal;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.BodyContentHandler;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.SAXException;

import org.springframework.stereotype.Component;

/** Extrai o texto do currículo (PDF ou DOCX) com o Apache Tika; o texto alimenta o score e as sugestões. */
@Component
public class ResumeTextExtractor {

	private static final Logger log = LoggerFactory.getLogger(ResumeTextExtractor.class);

	private static final int MAX_CHARACTERS = 100_000;

	/** Texto do arquivo, ou {@code null} se não houver texto legível (PDF escaneado, arquivo corrompido). */
	public @Nullable String extract(byte[] content) {
		return extractDocument(content).text();
	}

	/** Texto e número de páginas (quando o formato informa), para o teste do que o ATS lê. */
	public Extracted extractDocument(byte[] content) {
		BodyContentHandler handler = new BodyContentHandler(MAX_CHARACTERS);
		Metadata metadata = new Metadata();
		try (InputStream input = new ByteArrayInputStream(content)) {
			new AutoDetectParser().parse(input, handler, metadata, new ParseContext());
		}
		catch (IOException | SAXException | TikaException ex) {
			log.warn("Não foi possível extrair o texto do currículo: {}", ex.getMessage());
			return new Extracted(null, null);
		}
		String text = handler.toString().replaceAll("[ \\t\\x0B\\f\\r]+", " ").replaceAll("\\n\\s*\\n+", "\n\n").strip();
		return new Extracted(text.isEmpty() ? null : text, pages(metadata));
	}

	private static @Nullable Integer pages(Metadata metadata) {
		for (String name : new String[] { "xmpTPg:NPages", "meta:page-count", "Page-Count" }) {
			String value = metadata.get(name);
			if (value != null && value.strip().matches("\\d+")) {
				return Integer.parseInt(value.strip());
			}
		}
		return null;
	}

	public record Extracted(@Nullable String text, @Nullable Integer pages) {
	}

}
