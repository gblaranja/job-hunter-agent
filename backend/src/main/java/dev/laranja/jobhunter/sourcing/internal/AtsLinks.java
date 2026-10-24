package dev.laranja.jobhunter.sourcing.internal;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

import dev.laranja.jobhunter.companies.AtsBoard;
import dev.laranja.jobhunter.jobs.JobOrigin;

/**
 * Reconhece o ATS e o slug no link da página de carreira ou de uma vaga, como {@code jobs.lever.co/nubank} ou
 * {@code acme.teamtailor.com/jobs}. Páginas de carreira em domínio próprio da empresa não são reconhecidas.
 */
final class AtsLinks {

	private static final Pattern SLUG = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]{0,99}");

	/** Subdomínios dos próprios ATS que não são de uma empresa. */
	private static final Set<String> RESERVED = Set.of("www", "app", "api", "apply", "help", "support", "status",
			"careers", "jobs", "blog", "docs");

	private AtsLinks() {
	}

	static Optional<AtsBoard> detect(String link) {
		URI uri;
		try {
			uri = new URI(link.strip());
		}
		catch (URISyntaxException ex) {
			return Optional.empty();
		}
		if (uri.getHost() == null || uri.getScheme() == null || !uri.getScheme().toLowerCase(Locale.ROOT).startsWith("http")) {
			return Optional.empty();
		}
		String host = uri.getHost().toLowerCase(Locale.ROOT);
		List<String> path = Arrays.stream((uri.getPath() == null) ? new String[0] : uri.getPath().split("/"))
			.filter(segment -> !segment.isBlank())
			.toList();
		String first = path.isEmpty() ? null : path.getFirst();
		return switch (host) {
			case "boards.greenhouse.io", "job-boards.greenhouse.io", "boards.eu.greenhouse.io",
					"job-boards.eu.greenhouse.io" ->
				"embed".equals(first) ? board(JobOrigin.GREENHOUSE, queryParam(uri, "for")) : board(JobOrigin.GREENHOUSE, first);
			case "jobs.lever.co" -> board(JobOrigin.LEVER, first);
			case "jobs.ashbyhq.com" -> board(JobOrigin.ASHBY, first);
			case "jobs.smartrecruiters.com", "careers.smartrecruiters.com" -> board(JobOrigin.SMARTRECRUITERS, first);
			case "apply.workable.com" -> ("j".equals(first) || "api".equals(first)) ? Optional.empty()
					: board(JobOrigin.WORKABLE, first);
			default -> subdomain(host);
		};
	}

	/** ATS em que cada empresa tem um subdomínio próprio. */
	private record HostedBoard(String suffix, JobOrigin origin, boolean regional) {
	}

	private static final List<HostedBoard> HOSTED = List.of(new HostedBoard(".teamtailor.com", JobOrigin.TEAMTAILOR, true),
			new HostedBoard(".recruitee.com", JobOrigin.RECRUITEE, false),
			new HostedBoard(".workable.com", JobOrigin.WORKABLE, false));

	/**
	 * {@code acme.workable.com}, {@code acme.recruitee.com} e {@code acme.teamtailor.com}, inclusive os subdomínios
	 * regionais da Teamtailor ({@code acme.na.teamtailor.com}, cujo slug é {@code acme.na}).
	 */
	private static Optional<AtsBoard> subdomain(String host) {
		for (HostedBoard hosted : HOSTED) {
			if (host.endsWith(hosted.suffix())) {
				String slug = host.substring(0, host.length() - hosted.suffix().length());
				String company = slug.contains(".") ? slug.substring(0, slug.indexOf('.')) : slug;
				if (RESERVED.contains(company) || (slug.contains(".") && !hosted.regional())) {
					return Optional.empty();
				}
				return board(hosted.origin(), slug);
			}
		}
		return Optional.empty();
	}

	private static Optional<AtsBoard> board(JobOrigin origin, String slug) {
		if (slug == null || !SLUG.matcher(slug).matches()) {
			return Optional.empty();
		}
		return Optional.of(new AtsBoard(origin.name(), slug));
	}

	private static String queryParam(URI uri, String name) {
		if (uri.getQuery() == null) {
			return null;
		}
		for (String pair : uri.getQuery().split("&")) {
			int equals = pair.indexOf('=');
			if (equals > 0 && pair.substring(0, equals).equals(name)) {
				return pair.substring(equals + 1);
			}
		}
		return null;
	}

}
