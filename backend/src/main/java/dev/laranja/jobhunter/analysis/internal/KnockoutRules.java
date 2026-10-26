package dev.laranja.jobhunter.analysis.internal;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import dev.laranja.jobhunter.analysis.JobSalary;
import dev.laranja.jobhunter.analysis.KnockoutType;
import org.jspecify.annotations.Nullable;

/**
 * Eliminatórios e salário pela leitura da descrição, sem LLM: anos de experiência, nível de inglês, "residir em",
 * dias presenciais, formação, autorização de trabalho, fuso, regime e a faixa salarial. As regras rodam sobre o
 * texto sem acentos e em caixa baixa, mas com as mesmas posições do original, para o trecho sair como está na vaga.
 */
final class KnockoutRules {

	/** Separador entre palavras nos padrões: espaço, pontuação ou quebra de linha. */
	private static final String SEP = "[^\\p{L}\\p{N}]+";

	private static final Pattern NICE_TO_HAVE = pattern(
			"(diferencia(l|is)|desejave(l|is)|seria um plus|sera um plus|nice to have|bonus points|e um plus)");

	private static final Pattern YEARS_BEFORE = pattern("(?:minimo de |pelo menos |no minimo |at least |mais de )?"
			+ "(\\d{1,2})(?: (?:a|ate|to|ou) \\d{1,2})? ?(?:anos|ano|years|year|yrs)(?: ou mais| or more)?(?: de| of)?"
			+ "(?: solida| comprovada| profissional| previous| professional| relevant| hands on)? "
			+ "(?:experiencia|experience|atuacao|vivencia)");

	private static final Pattern YEARS_AFTER = pattern("(?:experiencia|experience)(?: profissional| comprovada)?"
			+ "(?: minima)?(?: de| of)?(?: no minimo| pelo menos| at least| minimum of)? (\\d{1,2})(?: (?:a|ate|to) \\d{1,2})? "
			+ "(?:anos|years)");

	private static final Pattern ENGLISH_LEVEL = pattern("(?:ingles|english)(?: [a-z]+){0,3}? (fluente|fluent|fluencia|"
			+ "fluency|nativo|native|avancado|advanced|intermediario|intermediate|conversacao|conversational|basico|basic|"
			+ "tecnico|technical|c1|c2|b1|b2)");

	private static final Pattern LEVEL_ENGLISH = pattern("(fluente|fluent|fluencia|fluency|avancado|advanced|"
			+ "intermediario|intermediate|basico|basic|tecnico|technical)(?: [a-z]+){0,2}? (?:em |no |in )?(?:ingles|english)");

	private static final Pattern RESIDE = pattern("(?:residir|morar|residente|resida|resides|reside|based|located|"
			+ "live|living) (?:(?:em|no|na|nos|nas|in|na regiao de|na cidade de|proximo a|proximo de|perto de) )?"
			+ "((?:\\p{L}+)(?: \\p{L}+){0,3})");

	private static final Set<String> PLACE_STOPWORDS = Set.of("ou", "e", "para", "com", "ate", "or", "and", "to",
			"with", "regiao", "region", "area", "metropolitana", "proximidades", "arredores", "sendo", "pois", "a", "o");

	private static final Pattern FULL_ONSITE = pattern(
			"(?:100 presencial|totalmente presencial|modelo presencial|trabalho presencial|regime presencial|fully on site|"
					+ "fully onsite|100 on site|100 onsite)");

	private static final Pattern ONSITE_DAYS = pattern("(\\d)\\s?(?:x|vezes|vez|dias|days|times)(?: (?:por|na|a|per|each) "
			+ "(?:semana|week))? (?:no |de |in the |at the |in |at )?(?:escritorio|presencial|presencialmente|office|on site|onsite)");

	private static final Pattern DEGREE = pattern("(?:ensino superior|formacao superior|graduacao|bacharelado|"
			+ "nivel superior|superior) (?:completo|completa|concluido|concluida)");

	private static final Pattern AUTHORIZATION = pattern("(?:authorized|authorization|eligible|elegivel|autorizacao|"
			+ "permissao|permission|right) (?:to |para )?(?:legally )?(?:work|trabalhar) (?:in |no |nos |na )?(?:the )?"
			+ "(usa|us|united states|estados unidos|eua|canada|uk|united kingdom|reino unido|portugal)");

	private static final Pattern TIMEZONE = pattern("(?:overlap|sobreposicao|timezone|time zone|fuso)(?: [a-z]+){0,5}? "
			+ "(est|edt|pst|pdt|cst|cdt|mst|et|pt|ct|cet|gmt|utc(?:\\s?[+-]\\s?\\d{1,2})?)(?![\\p{L}])");

	private static final Pattern ENGLISH_WORDS = Pattern.compile("(?<![\\p{L}])(the|and|with|you|our|we|will|are)(?![\\p{L}])");

	private static final Pattern PORTUGUESE_WORDS = Pattern
		.compile("(?<![\\p{L}])(de|com|voce|nossa|nosso|para|uma|sao|que)(?![\\p{L}])");

	private static final Pattern MONEY = Pattern.compile("(r\\$|us\\$|usd|brl|eur|€|\\$)\\s*(\\d{1,3}(?:[.,]\\d{3})+(?:[.,]\\d{1,2})?|\\d+(?:[.,]\\d+)?)"
			+ "\\s*(k|mil)?(?![\\p{L}])");

	private static final Pattern SALARY_CONTEXT = Pattern.compile("salari|remunera|faixa|salary|compensation|pay|bolsa|pretens");

	private static final Pattern BENEFIT_CONTEXT = Pattern.compile("(vr|va|vale|auxilio|bonus|beneficio|reembolso|plr)\\W*$");

	private static final Map<String, String> COUNTRIES = Map.of("usa", "US", "us", "US", "united states", "US",
			"estados unidos", "US", "eua", "US", "canada", "CA", "uk", "GB", "united kingdom", "GB", "reino unido", "GB",
			"portugal", "PT");

	private KnockoutRules() {
	}

	/**
	 * Eliminatórios encontrados na parte obrigatória da descrição (antes de "diferenciais").
	 *
	 * @param contractType CLT, PJ, AMBOS ou NAO_INFORMADO, já detectado
	 * @param workModel modelo de trabalho da vaga
	 * @param location localidade informada na vaga
	 */
	static List<KnockoutRequirement> extract(@Nullable String description, String contractType, String workModel,
			@Nullable String location) {
		String original = (description == null) ? "" : description;
		String folded = fold(original);
		Matcher niceSection = NICE_TO_HAVE.matcher(folded);
		int end = niceSection.find() ? niceSection.start() : folded.length();
		String required = folded.substring(0, end);
		Map<KnockoutType, KnockoutRequirement> found = new LinkedHashMap<>();

		Integer years = null;
		String yearsText = null;
		for (Pattern pattern : new Pattern[] { YEARS_BEFORE, YEARS_AFTER }) {
			Matcher matcher = pattern.matcher(required);
			while (matcher.find()) {
				int value = Integer.parseInt(matcher.group(1));
				if (value > 0 && value <= 20 && (years == null || value > years)) {
					years = value;
					yearsText = original.substring(matcher.start(), matcher.end());
				}
			}
		}
		if (years != null) {
			found.put(KnockoutType.ANOS_EXPERIENCIA,
					new KnockoutRequirement(KnockoutType.ANOS_EXPERIENCIA.name(), clean(yearsText), years.toString()));
		}

		english(required, original).ifPresent(knockout -> found.put(KnockoutType.IDIOMA, knockout));
		if (!found.containsKey(KnockoutType.IDIOMA) && writtenInEnglish(folded)) {
			found.put(KnockoutType.IDIOMA,
					new KnockoutRequirement(KnockoutType.IDIOMA.name(), "Vaga escrita em inglês", "INGLES:AVANCADO"));
		}

		Matcher reside = RESIDE.matcher(required);
		if (reside.find()) {
			String place = place(original.substring(reside.start(1), reside.end(1)));
			if (!place.isBlank()) {
				found.put(KnockoutType.LOCALIDADE, new KnockoutRequirement(KnockoutType.LOCALIDADE.name(),
						clean(original.substring(reside.start(), reside.end())), place));
			}
		}
		boolean onSiteJob = "PRESENCIAL".equals(workModel) || "HIBRIDO".equals(workModel);
		if (!found.containsKey(KnockoutType.LOCALIDADE) && onSiteJob && location != null && !location.isBlank()
				&& !fold(location).matches(".*(remot|brasil|brazil|anywhere).*")) {
			found.put(KnockoutType.LOCALIDADE, new KnockoutRequirement(KnockoutType.LOCALIDADE.name(),
					"Local da vaga: " + location.strip(), location.strip()));
		}

		if (!"REMOTO".equals(workModel)) {
			Matcher days = ONSITE_DAYS.matcher(required);
			Matcher full = FULL_ONSITE.matcher(required);
			if (days.find()) {
				found.put(KnockoutType.PRESENCIAL, new KnockoutRequirement(KnockoutType.PRESENCIAL.name(),
						clean(original.substring(days.start(), days.end())), days.group(1)));
			}
			else if (full.find()) {
				found.put(KnockoutType.PRESENCIAL, new KnockoutRequirement(KnockoutType.PRESENCIAL.name(),
						clean(original.substring(full.start(), full.end())), "5"));
			}
			else if ("PRESENCIAL".equals(workModel)) {
				found.put(KnockoutType.PRESENCIAL,
						new KnockoutRequirement(KnockoutType.PRESENCIAL.name(), "Modelo de trabalho: presencial", "5"));
			}
		}

		Matcher degree = DEGREE.matcher(required);
		if (degree.find()) {
			found.put(KnockoutType.FORMACAO, new KnockoutRequirement(KnockoutType.FORMACAO.name(),
					clean(original.substring(degree.start(), degree.end())), "SUPERIOR"));
		}
		Matcher authorization = AUTHORIZATION.matcher(required);
		if (authorization.find()) {
			found.put(KnockoutType.AUTORIZACAO_TRABALHO,
					new KnockoutRequirement(KnockoutType.AUTORIZACAO_TRABALHO.name(),
							clean(original.substring(authorization.start(), authorization.end())),
							COUNTRIES.getOrDefault(authorization.group(1), authorization.group(1).toUpperCase(Locale.ROOT))));
		}
		Matcher timezone = TIMEZONE.matcher(required);
		if (timezone.find()) {
			found.put(KnockoutType.FUSO, new KnockoutRequirement(KnockoutType.FUSO.name(),
					clean(original.substring(timezone.start(), timezone.end())), timezone.group(1).toUpperCase(Locale.ROOT)));
		}
		if ("PJ".equals(contractType) || "CLT".equals(contractType)) {
			found.put(KnockoutType.CONTRATO,
					new KnockoutRequirement(KnockoutType.CONTRATO.name(), "Contratação " + contractType, contractType));
		}
		return new ArrayList<>(found.values());
	}

	/** A faixa salarial informada na descrição; nula se não houver. */
	static @Nullable JobSalary salary(@Nullable String description, String contractType) {
		if (description == null) {
			return null;
		}
		String folded = fold(description);
		Matcher money = MONEY.matcher(folded);
		List<Matcher> hits = new ArrayList<>();
		List<int[]> spans = new ArrayList<>();
		List<BigDecimal> values = new ArrayList<>();
		List<String> currencies = new ArrayList<>();
		while (money.find()) {
			String before = folded.substring(Math.max(0, money.start() - 25), money.start());
			if (BENEFIT_CONTEXT.matcher(before.strip()).find()) {
				continue;
			}
			BigDecimal value = amount(money.group(2), money.group(3));
			if (value == null) {
				continue;
			}
			hits.add(money);
			spans.add(new int[] { money.start(), money.end() });
			values.add(value);
			currencies.add(currency(money.group(1)));
		}
		for (int i = 0; i < spans.size(); i++) {
			int start = spans.get(i)[0];
			String context = folded.substring(Math.max(0, start - 60), start);
			boolean range = i + 1 < spans.size() && spans.get(i + 1)[0] - spans.get(i)[1] <= 6;
			if (!range && !SALARY_CONTEXT.matcher(context).find()) {
				continue;
			}
			BigDecimal min = values.get(i);
			BigDecimal max = range ? values.get(i + 1) : min;
			int end = range ? spans.get(i + 1)[1] : spans.get(i)[1];
			String currency = currencies.get(i);
			String after = folded.substring(end, Math.min(folded.length(), end + 30));
			String period = period(after, currency, max);
			String window = folded.substring(Math.max(0, start - 60), Math.min(folded.length(), end + 30));
			String regime = window.matches("(?s).*(?<![\\p{L}])pj(?![\\p{L}]).*") ? "PJ"
					: window.matches("(?s).*(?<![\\p{L}])clt(?![\\p{L}]).*") ? "CLT"
							: ("PJ".equals(contractType) || "CLT".equals(contractType)) ? contractType : null;
			return new JobSalary(min, max, currency, period, regime,
					clean(description.substring(start, Math.min(description.length(), end))));
		}
		return null;
	}

	private static java.util.Optional<KnockoutRequirement> english(String required, String original) {
		String bestValue = null;
		String bestText = null;
		int bestRank = -1;
		for (Pattern pattern : new Pattern[] { ENGLISH_LEVEL, LEVEL_ENGLISH }) {
			Matcher matcher = pattern.matcher(required);
			while (matcher.find()) {
				var level = ResumeHints.level(matcher.group(1));
				if (level != null && level.ordinal() > bestRank) {
					bestRank = level.ordinal();
					bestValue = "INGLES:" + level.name();
					bestText = original.substring(matcher.start(), matcher.end());
				}
			}
		}
		return (bestValue == null) ? java.util.Optional.empty()
				: java.util.Optional.of(new KnockoutRequirement(KnockoutType.IDIOMA.name(), clean(bestText), bestValue));
	}

	private static boolean writtenInEnglish(String folded) {
		long english = ENGLISH_WORDS.matcher(folded).results().count();
		long portuguese = PORTUGUESE_WORDS.matcher(folded).results().count();
		return english >= 10 && english > portuguese * 2;
	}

	/** O lugar até a primeira palavra que não faz parte dele ("Campinas ou região" vira "Campinas"). */
	private static String place(String text) {
		List<String> words = new ArrayList<>();
		for (String word : text.strip().split("[^\\p{L}]+")) {
			if (PLACE_STOPWORDS.contains(fold(word))) {
				break;
			}
			words.add(word);
		}
		return String.join(" ", words);
	}

	private static @Nullable BigDecimal amount(String number, @Nullable String multiplier) {
		String digits = number;
		int dot = digits.lastIndexOf('.');
		int comma = digits.lastIndexOf(',');
		if (dot >= 0 && comma >= 0) {
			char decimal = (dot > comma) ? '.' : ',';
			char thousands = (decimal == '.') ? ',' : '.';
			digits = digits.replace(String.valueOf(thousands), "").replace(decimal, '.');
		}
		else if (dot >= 0 || comma >= 0) {
			int index = Math.max(dot, comma);
			char separator = digits.charAt(index);
			boolean thousands = digits.length() - index - 1 == 3 && digits.chars().filter(c -> c == separator).count() >= 1
					&& multiplier == null;
			digits = thousands ? digits.replace(String.valueOf(separator), "") : digits.replace(separator, '.');
		}
		try {
			BigDecimal value = new BigDecimal(digits);
			if (multiplier != null) {
				value = value.multiply(BigDecimal.valueOf(1000));
			}
			return (value.compareTo(BigDecimal.valueOf(100)) < 0) ? null : value.stripTrailingZeros();
		}
		catch (NumberFormatException ex) {
			return null;
		}
	}

	private static String currency(String symbol) {
		return switch (symbol) {
			case "r$", "brl" -> "BRL";
			case "eur", "€" -> "EUR";
			default -> "USD";
		};
	}

	private static String period(String after, String currency, BigDecimal value) {
		if (after.matches("(?s)\\s*(/|por |ao |a |per |an? )?\\s*(mes|mensal|month|mo(?![\\p{L}])).*")) {
			return "MENSAL";
		}
		if (after.matches("(?s)\\s*(/|por |ao |a |per |an? )?\\s*(ano|anual|year|annual|yr|annum).*")) {
			return "ANUAL";
		}
		if (after.matches("(?s)\\s*(/|por |a |per |an? )?\\s*(hora|hour|hr|h(?![\\p{L}])).*")) {
			return "HORA";
		}
		BigDecimal yearly = "BRL".equals(currency) ? BigDecimal.valueOf(100_000) : BigDecimal.valueOf(20_000);
		return (value.compareTo(yearly) > 0) ? "ANUAL" : "MENSAL";
	}

	/** Caixa baixa e sem acentos, com o mesmo comprimento do original. */
	static String fold(String text) {
		StringBuilder folded = new StringBuilder(text.length());
		for (int i = 0; i < text.length(); i++) {
			char c = text.charAt(i);
			String decomposed = Normalizer.normalize(String.valueOf(c), Normalizer.Form.NFD);
			folded.append(Character.toLowerCase(decomposed.charAt(0)));
		}
		return folded.toString();
	}

	private static String clean(String text) {
		String compact = text.strip().replaceAll("\\s+", " ");
		return (compact.length() > 160) ? compact.substring(0, 160) : compact;
	}

	/** Compila o padrão trocando cada espaço por qualquer separador entre palavras. */
	private static Pattern pattern(String regex) {
		return Pattern.compile(regex.replace(" ", SEP));
	}

}
