package dev.laranja.jobhunter.sourcing.internal;

import java.text.NumberFormat;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import dev.laranja.jobhunter.jobs.WorkModel;
import org.jspecify.annotations.Nullable;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Lê o {@code JobPosting} do schema.org publicado em JSON-LD pelas páginas de vaga (ATS, sites de carreira). Aceita
 * um objeto, um array ou um {@code @graph}.
 */
final class JobPostingJsonLd {

	private JobPostingJsonLd() {
	}

	/**
	 * @param validThrough fim das inscrições, quando a página informa
	 */
	record Posting(@Nullable String title, @Nullable String company, @Nullable String location,
			@Nullable WorkModel workModel, @Nullable String salaryRange, @Nullable String description,
			@Nullable Instant validThrough) {
	}

	/** O primeiro {@code JobPosting} encontrado nos blocos de JSON-LD (cada bloco é um texto JSON). */
	static Optional<Posting> parse(JsonMapper json, List<String> blocks) {
		for (String block : blocks) {
			JsonNode root;
			try {
				root = json.readTree(block.strip());
			}
			catch (JacksonException ex) {
				continue;
			}
			Optional<JsonNode> posting = find(root, 0);
			if (posting.isPresent()) {
				return Optional.of(map(posting.get()));
			}
		}
		return Optional.empty();
	}

	private static Optional<JsonNode> find(@Nullable JsonNode node, int depth) {
		if (node == null || depth > 4) {
			return Optional.empty();
		}
		if (node.isArray()) {
			for (JsonNode item : node.values()) {
				Optional<JsonNode> found = find(item, depth + 1);
				if (found.isPresent()) {
					return found;
				}
			}
			return Optional.empty();
		}
		if (!node.isObject()) {
			return Optional.empty();
		}
		if (isJobPosting(node.get("@type"))) {
			return Optional.of(node);
		}
		return find(node.get("@graph"), depth + 1);
	}

	private static boolean isJobPosting(@Nullable JsonNode type) {
		if (type == null) {
			return false;
		}
		if (type.isArray()) {
			return type.values().stream().anyMatch(JobPostingJsonLd::isJobPosting);
		}
		return type.isString() && type.asString().endsWith("JobPosting");
	}

	private static Posting map(JsonNode posting) {
		String title = text(posting.get("title"));
		JsonNode organization = posting.get("hiringOrganization");
		String company = (organization != null && organization.isObject()) ? text(organization.get("name"))
				: text(organization);
		boolean remote = "TELECOMMUTE".equalsIgnoreCase(firstText(posting.get("jobLocationType")));
		String location = location(posting.get("jobLocation"));
		String applicants = applicantLocation(posting.get("applicantLocationRequirements"));
		if (remote) {
			String where = (applicants != null) ? applicants : location;
			location = "Remoto" + ((where != null) ? " (" + where + ")" : "");
		}
		String description = text(posting.get("description"));
		if (description != null) {
			description = HtmlText.toText(description, description.contains("&lt;"));
		}
		return new Posting(title, company, location, remote ? WorkModel.REMOTO : null, salary(posting.get("baseSalary")),
				description, instant(text(posting.get("validThrough"))));
	}

	private static @Nullable String location(@Nullable JsonNode places) {
		if (places == null) {
			return null;
		}
		Set<String> names = new LinkedHashSet<>();
		List<JsonNode> list = places.isArray() ? new ArrayList<>(places.values()) : List.of(places);
		for (JsonNode place : list) {
			JsonNode address = place.isObject() ? place.get("address") : null;
			if (address == null || !address.isObject()) {
				String name = place.isObject() ? text(place.get("name")) : text(place);
				if (name != null) {
					names.add(name);
				}
				continue;
			}
			List<String> parts = new ArrayList<>();
			for (String field : List.of("addressLocality", "addressRegion")) {
				String value = text(address.get(field));
				if (value != null && !parts.contains(value)) {
					parts.add(value);
				}
			}
			if (parts.isEmpty()) {
				JsonNode country = address.get("addressCountry");
				String value = (country != null && country.isObject()) ? text(country.get("name")) : text(country);
				if (value != null) {
					parts.add(value);
				}
			}
			if (!parts.isEmpty()) {
				names.add(String.join(", ", parts));
			}
		}
		return names.isEmpty() ? null : String.join(" · ", names);
	}

	private static @Nullable String applicantLocation(@Nullable JsonNode requirements) {
		if (requirements == null) {
			return null;
		}
		List<JsonNode> list = requirements.isArray() ? new ArrayList<>(requirements.values()) : List.of(requirements);
		List<String> names = list.stream()
			.map(node -> node.isObject() ? text(node.get("name")) : text(node))
			.filter(Objects::nonNull)
			.toList();
		return names.isEmpty() ? null : String.join(", ", names);
	}

	private static @Nullable String salary(@Nullable JsonNode salary) {
		if (salary == null || !salary.isObject()) {
			return null;
		}
		String currency = text(salary.get("currency"));
		JsonNode value = salary.get("value");
		Double min;
		Double max = null;
		String unit = null;
		if (value != null && value.isObject()) {
			min = number(value.get("minValue"));
			max = number(value.get("maxValue"));
			if (min == null && max == null) {
				min = number(value.get("value"));
			}
			unit = text(value.get("unitText"));
		}
		else {
			min = number(value);
		}
		if (min == null && max == null) {
			return null;
		}
		String symbol = (currency == null || currency.equalsIgnoreCase("BRL")) ? "R$" : currency.toUpperCase(Locale.ROOT);
		String range = (min != null && max != null && !min.equals(max)) ? format(min) + "–" + format(max)
				: format((min != null) ? min : max);
		return symbol + " " + range + period(unit);
	}

	private static String period(@Nullable String unit) {
		if (unit == null) {
			return "";
		}
		return switch (unit.toUpperCase(Locale.ROOT)) {
			case "MONTH" -> "/mês";
			case "YEAR" -> "/ano";
			case "HOUR" -> "/hora";
			case "WEEK" -> "/semana";
			case "DAY" -> "/dia";
			default -> "";
		};
	}

	private static String format(double value) {
		NumberFormat format = NumberFormat.getNumberInstance(Locale.forLanguageTag("pt-BR"));
		format.setMaximumFractionDigits(0);
		return format.format(value);
	}

	private static @Nullable Double number(@Nullable JsonNode node) {
		if (node == null) {
			return null;
		}
		if (node.isNumber()) {
			return node.doubleValue();
		}
		if (node.isString()) {
			try {
				return Double.parseDouble(node.asString().replace(",", "."));
			}
			catch (NumberFormatException ex) {
				return null;
			}
		}
		return null;
	}

	private static @Nullable Instant instant(@Nullable String value) {
		if (value == null) {
			return null;
		}
		try {
			return OffsetDateTime.parse(value).toInstant();
		}
		catch (DateTimeParseException ex) {
			try {
				return LocalDate.parse(value.substring(0, Math.min(10, value.length())))
					.atStartOfDay(ZoneOffset.UTC)
					.toInstant();
			}
			catch (DateTimeParseException ignored) {
				return null;
			}
		}
	}

	private static @Nullable String firstText(@Nullable JsonNode node) {
		if (node != null && node.isArray()) {
			return node.values().stream().map(JobPostingJsonLd::text).filter(Objects::nonNull).findFirst().orElse(null);
		}
		return text(node);
	}

	private static @Nullable String text(@Nullable JsonNode node) {
		if (node == null || node.isNull() || !(node.isString() || node.isNumber())) {
			return null;
		}
		String value = node.asString().strip();
		return value.isEmpty() ? null : value;
	}

}
