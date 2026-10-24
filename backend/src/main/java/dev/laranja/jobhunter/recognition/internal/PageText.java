package dev.laranja.jobhunter.recognition.internal;

import java.net.URI;

import dev.laranja.jobhunter.shared.BusinessRuleException;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/** Baixa a página de uma lista de premiadas e devolve o texto, com uma linha por item. */
@Component
public class PageText {

	private final RestClient client;

	PageText(RestClient.Builder http) {
		this.client = http.clone().defaultHeader("User-Agent", "job-hunter-agent (uso pessoal)").build();
	}

	public String fetch(String url) {
		URI uri = URI.create(url.strip());
		if (!"https".equalsIgnoreCase(uri.getScheme()) && !"http".equalsIgnoreCase(uri.getScheme())) {
			throw new BusinessRuleException("Informe um link http ou https.");
		}
		String html;
		try {
			html = this.client.get().uri(uri).accept(MediaType.TEXT_HTML, MediaType.ALL).retrieve().body(String.class);
		}
		catch (RestClientException ex) {
			throw new BusinessRuleException("Não foi possível abrir o link: " + ex.getMessage());
		}
		return toText((html != null) ? html : "");
	}

	static String toText(String html) {
		Document document = Jsoup.parse(html);
		document.select("script, style, noscript, nav, header, footer, form").remove();
		for (Element block : document.select("p, li, br, h1, h2, h3, h4, h5, h6, div, tr, td")) {
			block.after("\n");
		}
		return document.body()
			.wholeText()
			.replace('\u00A0', ' ')
			.replaceAll("[ \\t]+", " ")
			.replaceAll(" *\\n *", "\n")
			.replaceAll("\\n{2,}", "\n")
			.strip();
	}

}
