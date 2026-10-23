package dev.laranja.jobhunter.sourcing.internal;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import dev.laranja.jobhunter.jobs.JobOrigin;
import dev.laranja.jobhunter.jobs.RawJob;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import org.springframework.stereotype.Component;

/**
 * Alertas de vaga da Gupy. As vagas linkam para a página de carreiras da empresa
 * ({@code https://<empresa>.gupy.io/jobs/<id>}); sem o nome da empresa no texto, ele vem do subdomínio.
 */
@Component
class GupyAlertParser implements AlertParser {

	private static final Pattern JOB_LINK = Pattern.compile("https?://([a-z0-9-]+)\\.gupy\\.io/jobs/(\\d+)");

	private static final Pattern SENDER = Pattern.compile("(?i)@([a-z0-9-]+\\.)*gupy\\.(io|com\\.br)$");

	@Override
	public boolean supports(String sender) {
		return SENDER.matcher(sender).find();
	}

	@Override
	public List<RawJob> parse(String html) {
		Document document = Jsoup.parse(html);
		Map<String, Element> titleLinks = new LinkedHashMap<>();
		Map<String, String> slugs = new LinkedHashMap<>();
		for (Element link : document.select("a[href]")) {
			Matcher matcher = JOB_LINK.matcher(link.attr("href"));
			if (matcher.find() && !link.text().isBlank() && !NOISE.matcher(link.text().strip()).matches()) {
				titleLinks.putIfAbsent(matcher.group(2), link);
				slugs.putIfAbsent(matcher.group(2), matcher.group(1));
			}
		}
		List<RawJob> jobs = new ArrayList<>();
		titleLinks.forEach((jobId, link) -> toJob(jobId, slugs.get(jobId), link).ifPresent(jobs::add));
		return jobs;
	}

	private Optional<RawJob> toJob(String jobId, String slug, Element titleLink) {
		String title = titleLink.text().strip();
		Element card = AlertParser.cardOf(titleLink, JOB_LINK, jobId);
		List<String> blocks = (card != null) ? AlertParser.textBlocks(card) : List.of();
		int titleIndex = blocks.indexOf(title);
		List<String> after = (titleIndex >= 0) ? blocks.subList(titleIndex + 1, blocks.size()) : List.of();
		String company = !after.isEmpty() ? after.get(0) : companyFromSlug(slug);
		String location = (after.size() > 1) ? after.get(1) : null;
		if (company.contains("·")) {
			String[] parts = company.split("·", 2);
			company = parts[0].strip();
			location = parts[1].strip();
		}
		return Optional.of(new RawJob(JobOrigin.GUPY_ALERTA, jobId, title, company, location, null, null,
				"https://" + slug + ".gupy.io/jobs/" + jobId, null));
	}

	// "banco-exemplo" vira "Banco Exemplo".
	private static String companyFromSlug(String slug) {
		StringBuilder name = new StringBuilder();
		for (String part : slug.split("-")) {
			if (!part.isEmpty()) {
				name.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1).toLowerCase(Locale.ROOT)).append(' ');
			}
		}
		return name.toString().strip();
	}

}
