package dev.laranja.jobhunter.network.internal;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jspecify.annotations.Nullable;

/**
 * Link do perfil no formato que identifica a pessoa: "http://br.linkedin.com/in/Ana-Lima/?trk=x" e
 * "https://www.linkedin.com/in/ana-lima" são o mesmo contato. Links de outros sites ficam como vieram.
 */
public final class ProfileUrls {

	private static final Pattern LINKEDIN = Pattern.compile("(?i)^https?://([a-z]{2,3}\\.|www\\.)?linkedin\\.com/in/([^/?#]+)");

	private ProfileUrls() {
	}

	public static @Nullable String normalize(@Nullable String url) {
		if (url == null || url.isBlank()) {
			return null;
		}
		String trimmed = url.strip();
		Matcher matcher = LINKEDIN.matcher(trimmed);
		if (matcher.find()) {
			return "https://www.linkedin.com/in/" + matcher.group(2).toLowerCase(Locale.ROOT);
		}
		return trimmed;
	}

}
