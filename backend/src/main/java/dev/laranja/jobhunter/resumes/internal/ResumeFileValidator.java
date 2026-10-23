package dev.laranja.jobhunter.resumes.internal;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import dev.laranja.jobhunter.shared.BusinessRuleException;
import org.apache.tika.Tika;

import org.springframework.stereotype.Component;

/**
 * Valida o arquivo pelo conteúdo, nunca pela extensão: um executável renomeado para .pdf é recusado, e um
 * PDF renomeado para .docx é aceito como PDF.
 */
@Component
public class ResumeFileValidator {

	static final long MAX_SIZE_BYTES = 5L * 1024 * 1024;

	private static final Set<String> ZIP_TYPES = Set.of("application/zip", "application/x-tika-ooxml",
			ResumeFileType.DOCX.contentType());

	private final Tika tika = new Tika();

	public ResumeFileType validate(byte[] content) {
		if (content.length == 0) {
			throw new BusinessRuleException("O arquivo está vazio.");
		}
		if (content.length > MAX_SIZE_BYTES) {
			throw new BusinessRuleException("O currículo deve ter no máximo 5 MB.");
		}
		String detected = this.tika.detect(content);
		if (ResumeFileType.PDF.contentType().equals(detected)) {
			return ResumeFileType.PDF;
		}
		if (ZIP_TYPES.contains(detected) && isWordDocument(content)) {
			return ResumeFileType.DOCX;
		}
		throw new BusinessRuleException("Envie o currículo em PDF ou DOCX.");
	}

	// Um DOCX é um zip com o manifesto OOXML e o corpo do documento do Word.
	private static boolean isWordDocument(byte[] content) {
		Set<String> entries = new HashSet<>();
		try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(content))) {
			ZipEntry entry;
			while ((entry = zip.getNextEntry()) != null) {
				entries.add(entry.getName());
			}
		}
		catch (IOException ex) {
			return false;
		}
		return entries.contains("[Content_Types].xml") && entries.contains("word/document.xml");
	}

}
