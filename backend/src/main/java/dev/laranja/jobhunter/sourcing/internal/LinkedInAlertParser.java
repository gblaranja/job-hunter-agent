package dev.laranja.jobhunter.sourcing.internal;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
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
 * Alertas de vaga do LinkedIn. Cada vaga aparece como um link {@code /jobs/view/<id>} (às vezes repetido no
 * logo), seguido de "Empresa · Localidade". O link é reduzido à URL canônica, sem os parâmetros de rastreio.
 */
@Component
class LinkedInAlertParser implements AlertParser {

	private static final Pattern JOB_LINK = Pattern.compile("linkedin\\.com/(?:comm/)?jobs/view/(\\d+)");

	private static final Pattern SENDER = Pattern.compile("(?i)@linkedin\\.com$");

	private static final Pattern SALARY = Pattern.compile("(?i)(R\\$|US\\$|\\$|€).*\\d");

	@Override
	public boolean supports(String sender) {
		return SENDER.matcher(sender).find();
	}

	@Override
	public List<RawJob> parse(String html) {
		Document document = Jsoup.parse(html);
		Map<String, Element> titleLinks = new LinkedHashMap<>();
		for (Element link : document.select("a[href]")) {
			Matcher matcher = JOB_LINK.matcher(link.attr("href"));
			if (matcher.find() && !link.text().isBlank() && !NOISE.matcher(link.text().strip()).matches()
					&& !wrapsAnotherLink(link)) {
				titleLinks.putIfAbsent(matcher.group(1), link);
			}
		}
		List<RawJob> jobs = new ArrayList<>();
		titleLinks.forEach((jobId, link) -> toJob(jobId, link).ifPresent(jobs::add));
		return jobs;
	}

	private Optional<RawJob> toJob(String jobId, Element titleLink) {
		String title = titleLink.text().strip();
		Element card = AlertParser.cardOf(titleLink, JOB_LINK, jobId);
		if (card == null) {
			return Optional.empty();
		}
		List<String> blocks = AlertParser.textBlocks(card);
		int titleIndex = blocks.indexOf(title);
		List<String> after = (titleIndex >= 0) ? blocks.subList(titleIndex + 1, blocks.size()) : blocks;
		String company = null;
		String location = null;
		String salary = null;
		for (String block : after) {
			if (SALARY.matcher(block).find()) {
				salary = block;
			}
			else if (company == null && block.contains("·")) {
				String[] parts = block.split("·", 2);
				company = parts[0].strip();
				location = parts[1].strip();
			}
			else if (company == null) {
				company = block;
			}
			else if (location == null) {
				location = block;
			}
		}
		if (company == null || company.isBlank()) {
			return Optional.empty();
		}
		return Optional.of(new RawJob(JobOrigin.LINKEDIN_ALERTA, jobId, title, company, location, null, salary,
				"https://www.linkedin.com/jobs/view/" + jobId + "/", null));
	}

	/**
	 * O card inteiro costuma ser um link para a vaga, com o link do título aninhado dentro. O texto do link de
	 * fora junta título, empresa e localidade; só o de dentro é o título.
	 */
	private static boolean wrapsAnotherLink(Element link) {
		return link.select("a[href]").size() > 1;
	}

}
