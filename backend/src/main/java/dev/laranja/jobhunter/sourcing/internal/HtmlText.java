package dev.laranja.jobhunter.sourcing.internal;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.parser.Parser;

/** Descrição em HTML (dos ATS e agregadores) convertida em texto com quebras de linha e marcadores de lista. */
final class HtmlText {

	private HtmlText() {
	}

	static String toText(String html, boolean escaped) {
		if (html == null || html.isBlank()) {
			return null;
		}
		Document document = Jsoup.parse(escaped ? Parser.unescapeEntities(html, false) : html);
		for (Element item : document.select("li")) {
			item.prependText("- ");
		}
		for (Element block : document.select("p, li, br, h1, h2, h3, h4, div")) {
			block.after("\n");
		}
		String text = document.wholeText().replace(' ', ' ');
		return text.replaceAll("[ \\t]+", " ").replaceAll(" *\\n *", "\n").replaceAll("\\n{3,}", "\n\n").strip();
	}

}
