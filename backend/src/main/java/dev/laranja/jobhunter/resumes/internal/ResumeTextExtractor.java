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
		BodyContentHandler handler = new BodyContentHandler(MAX_CHARACTERS);
		try (InputStream input = new ByteArrayInputStream(content)) {
			new AutoDetectParser().parse(input, handler, new Metadata(), new ParseContext());
		}
		catch (IOException | SAXException | TikaException ex) {
			log.warn("Não foi possível extrair o texto do currículo: {}", ex.getMessage());
			return null;
		}
		String text = handler.toString().replaceAll("[ \\t\\x0B\\f\\r]+", " ").replaceAll("\\n\\s*\\n+", "\n\n").strip();
		return text.isEmpty() ? null : text;
	}

}
