package dev.laranja.jobhunter;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** Arquivos mínimos de currículo para os testes; o marcador muda o conteúdo (e o sha256) de cada um. */
public final class TestFiles {

	private TestFiles() {
	}

	public static byte[] pdf(String marker) {
		return ("%PDF-1.4\n% " + marker + "\n1 0 obj << /Type /Catalog >> endobj\ntrailer << /Root 1 0 R >>\n%%EOF\n")
			.getBytes(StandardCharsets.ISO_8859_1);
	}

	public static byte[] docx(String marker) {
		var bytes = new ByteArrayOutputStream();
		try (var zip = new ZipOutputStream(bytes)) {
			zip.putNextEntry(new ZipEntry("[Content_Types].xml"));
			zip.write(("<?xml version=\"1.0\"?><Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">"
					+ "<Override PartName=\"/word/document.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml\"/>"
					+ "</Types>")
				.getBytes(StandardCharsets.UTF_8));
			zip.closeEntry();
			zip.putNextEntry(new ZipEntry("word/document.xml"));
			zip.write(("<w:document><w:body><w:p><w:r><w:t>" + marker + "</w:t></w:r></w:p></w:body></w:document>")
				.getBytes(StandardCharsets.UTF_8));
			zip.closeEntry();
		}
		catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
		return bytes.toByteArray();
	}

	public static byte[] executable() {
		return new byte[] { 0x7F, 'E', 'L', 'F', 2, 1, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0 };
	}

}
