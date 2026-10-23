package dev.laranja.jobhunter.sourcing.internal;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import dev.laranja.jobhunter.jobs.RawJob;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.TextNode;
import org.jspecify.annotations.Nullable;

/** Lê as vagas de um e-mail de alerta de uma plataforma. Cada remetente tem um layout diferente. */
public interface AlertParser {

	boolean supports(String sender);

	List<RawJob> parse(String html);

	/** Linhas que aparecem nos cards dos alertas mas não são empresa nem localidade. */
	Pattern NOISE = Pattern.compile(
			"(?i)^(candidatura simplificada|easy apply|promovida|promoted|novo|new|ver vaga|view job|ver todas.*|see all.*|"
					+ "candidate-se|apply( now)?|recrutando ativamente|actively recruiting|.*ex-alunos.*|.*alumni.*|"
					+ ".*conex(ão|ões).*|.*connections?.*|há \\d+.*|\\d+ (dias?|horas?|semanas?) atrás|"
					+ "\\d+ (days?|hours?|weeks?) ago|.*candidaturas?|.*applicants?)$");

	/**
	 * Blocos de texto do card na ordem em que aparecem, sem repetições e sem ruído. Os e-mails usam tabelas
	 * aninhadas; cada linha visível vira um bloco.
	 */
	static List<String> textBlocks(Element container) {
		List<String> blocks = new ArrayList<>();
		for (Element element : container.getAllElements()) {
			StringBuilder own = new StringBuilder();
			for (var node : element.childNodes()) {
				if (node instanceof TextNode text) {
					own.append(text.text());
				}
			}
			String text = own.toString().replace(' ', ' ').replaceAll("\\s+", " ").strip();
			if (!text.isEmpty() && !NOISE.matcher(text).matches() && !blocks.contains(text)) {
				blocks.add(text);
			}
		}
		return blocks;
	}

	/** Sobe a partir do link até o menor ancestral que contém mais texto que o título, sem incluir outra vaga. */
	static @Nullable Element cardOf(Element titleLink, Pattern sameJobLink, String jobId) {
		Element current = titleLink.parent();
		Element best = null;
		while (current != null && !current.tagName().equals("body")) {
			boolean onlyThisJob = current.select("a[href]")
				.stream()
				.map(link -> sameJobLink.matcher(link.attr("href")))
				.filter(Matcher::find)
				.allMatch(matcher -> matcher.group(matcher.groupCount()).equals(jobId));
			if (!onlyThisJob) {
				break;
			}
			best = current;
			if (textBlocks(current).size() >= 3) {
				break;
			}
			current = current.parent();
		}
		return best;
	}

}
