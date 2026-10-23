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

	/** PDF de uma página com o texto informado, legível pelo Tika (PDFBox). */
	public static byte[] pdfWithText(String text) {
		String content = "BT /F1 12 Tf 72 720 Td (" + text.replace("(", "").replace(")", "") + ") Tj ET";
		String[] objects = { "<< /Type /Catalog /Pages 2 0 R >>", "<< /Type /Pages /Kids [3 0 R] /Count 1 >>",
				"<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] /Contents 4 0 R /Resources << /Font << /F1 5 0 R >> >> >>",
				"<< /Length " + content.length() + " >>\nstream\n" + content + "\nendstream",
				"<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>" };
		StringBuilder pdf = new StringBuilder("%PDF-1.4\n");
		int[] offsets = new int[objects.length];
		for (int i = 0; i < objects.length; i++) {
			offsets[i] = pdf.length();
			pdf.append(i + 1).append(" 0 obj\n").append(objects[i]).append("\nendobj\n");
		}
		int xref = pdf.length();
		pdf.append("xref\n0 ").append(objects.length + 1).append("\n0000000000 65535 f \n");
		for (int offset : offsets) {
			pdf.append(String.format("%010d 00000 n \n", offset));
		}
		pdf.append("trailer\n<< /Size ").append(objects.length + 1).append(" /Root 1 0 R >>\nstartxref\n")
			.append(xref).append("\n%%EOF\n");
		return pdf.toString().getBytes(StandardCharsets.ISO_8859_1);
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
