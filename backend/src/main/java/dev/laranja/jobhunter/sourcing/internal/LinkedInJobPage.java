package dev.laranja.jobhunter.sourcing.internal;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.jspecify.annotations.Nullable;

/** A página pública de uma vaga do LinkedIn (a que abre sem login): só a descrição interessa. */
final class LinkedInJobPage {

	/** Menos que isso é um resumo ou uma página de login, não a descrição. */
	private static final int MIN_LENGTH = 80;

	private LinkedInJobPage() {
	}

	static @Nullable String description(@Nullable String html) {
		if (html == null || html.isBlank()) {
			return null;
		}
		Element markup = Jsoup.parse(html).selectFirst(".show-more-less-html__markup, .description__text");
		if (markup == null) {
			return null;
		}
		String text = HtmlText.toText(markup.html(), false);
		return (text == null || text.length() < MIN_LENGTH) ? null : text;
	}

}
