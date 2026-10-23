package dev.laranja.jobhunter.tailoring.internal;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.poi.xwpf.usermodel.Borders;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;

import org.springframework.stereotype.Component;

/**
 * Gera o DOCX do currículo adaptado a partir do Markdown restrito do prompt ({@code #}, {@code ##}, {@code ###},
 * bullets, negrito, itálico e links). O layout é o que os ATS leem melhor: uma coluna, sem tabelas, imagens ou
 * caixas de texto, títulos de seção como texto e bullets como caracteres.
 */
@Component
public class MarkdownDocx {

	private static final String FONT = "Calibri";

	// Itálico com sublinhado só fora de palavras, para não pegar e-mails e URLs como "joao_silva".
	private static final Pattern INLINE = Pattern
		.compile("\\*\\*(.+?)\\*\\*|\\*(.+?)\\*|(?<!\\w)_(.+?)_(?!\\w)|\\[([^\\]]+)]\\(([^)]+)\\)");

	private static final Pattern BULLET = Pattern.compile("^\\s*([-*•+])\\s+(.*)$");

	private static final Pattern ESCAPE = Pattern.compile("\\\\([\\\\*_\\[\\]()#+\\-.!`])");

	public byte[] write(String markdown, String title) {
		try (XWPFDocument document = new XWPFDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
			document.getProperties().getCoreProperties().setTitle(title);
			for (String raw : markdown.replace("\r", "").split("\n")) {
				String line = raw.stripTrailing();
				if (line.isBlank() || line.strip().matches("-{3,}|\\*{3,}|_{3,}")) {
					continue;
				}
				Matcher bullet = BULLET.matcher(line);
				if (line.startsWith("### ")) {
					XWPFParagraph paragraph = paragraph(document, 160, 0);
					inline(paragraph, line.substring(4).strip(), 11, true);
				}
				else if (line.startsWith("## ")) {
					XWPFParagraph paragraph = paragraph(document, 240, 80);
					paragraph.setBorderBottom(Borders.SINGLE);
					inline(paragraph, line.substring(3).strip(), 12, true);
				}
				else if (line.startsWith("# ")) {
					XWPFParagraph paragraph = paragraph(document, 0, 60);
					inline(paragraph, line.substring(2).strip(), 18, true);
				}
				else if (bullet.matches()) {
					XWPFParagraph paragraph = paragraph(document, 0, 40);
					paragraph.setIndentationLeft(360);
					paragraph.setIndentationHanging(240);
					XWPFRun mark = run(paragraph, 10.5, false);
					mark.setText("•");
					mark.addTab();
					inline(paragraph, bullet.group(2).strip(), 10.5, false);
				}
				else {
					XWPFParagraph paragraph = paragraph(document, 0, 60);
					inline(paragraph, line.strip(), 10.5, false);
				}
			}
			document.write(out);
			return out.toByteArray();
		}
		catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
	}

	private static XWPFParagraph paragraph(XWPFDocument document, int before, int after) {
		XWPFParagraph paragraph = document.createParagraph();
		paragraph.setSpacingBefore(before);
		paragraph.setSpacingAfter(after);
		return paragraph;
	}

	/** Texto com negrito, itálico e links; o link vira "texto (endereço)" para o endereço continuar legível. */
	private static void inline(XWPFParagraph paragraph, String text, double size, boolean bold) {
		Matcher matcher = INLINE.matcher(text);
		int position = 0;
		while (matcher.find()) {
			plain(paragraph, text.substring(position, matcher.start()), size, bold, false);
			if (matcher.group(1) != null) {
				plain(paragraph, matcher.group(1), size, true, false);
			}
			else if (matcher.group(2) != null || matcher.group(3) != null) {
				plain(paragraph, (matcher.group(2) != null) ? matcher.group(2) : matcher.group(3), size, bold, true);
			}
			else {
				String label = matcher.group(4);
				String url = matcher.group(5);
				plain(paragraph, label.equals(url) ? url : label + " (" + url + ")", size, bold, false);
			}
			position = matcher.end();
		}
		plain(paragraph, text.substring(position), size, bold, false);
	}

	private static void plain(XWPFParagraph paragraph, String text, double size, boolean bold, boolean italic) {
		if (text.isEmpty()) {
			return;
		}
		XWPFRun run = run(paragraph, size, bold);
		run.setItalic(italic);
		run.setText(ESCAPE.matcher(text).replaceAll("$1"));
	}

	private static XWPFRun run(XWPFParagraph paragraph, double size, boolean bold) {
		XWPFRun run = paragraph.createRun();
		run.setFontFamily(FONT);
		run.setFontSize(size);
		run.setBold(bold);
		return run;
	}

}
