package dev.laranja.jobhunter.jobs.internal;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import dev.laranja.jobhunter.jobs.InternationalEligibility;
import dev.laranja.jobhunter.jobs.JobScope;
import dev.laranja.jobhunter.shared.TextNormalizer;
import org.jspecify.annotations.Nullable;

/**
 * Classifica a vaga como nacional ou internacional, nesta ordem:
 * <ol>
 * <li>localidade fora do Brasil (país ou cidade estrangeira) é internacional;</li>
 * <li>com descrição, vale o idioma dela: conta palavras funcionais do inglês e do português, que aparecem em
 * qualquer texto corrido, e só chama de inglês o texto em que elas predominam com folga;</li>
 * <li>sem descrição (os alertas do LinkedIn só trazem título, empresa e local), vale o idioma do título: em inglês
 * e sem nenhuma marca do português ("Sênior", "Pleno", "Remoto", "Desenvolvedor"...).</li>
 * </ol>
 * Na dúvida, fica nacional: a seção nacional é a prioridade e uma vaga brasileira escondida custa mais caro.
 * <p>
 * Para as vagas do exterior, {@link #international} lê também de onde a pessoa pode ser contratada, o fuso exigido
 * e se a contratação é como contractor.
 */
public final class JobScopeDetector {

	// Sem as que existem nas duas línguas ("a", "do", "no", "as", "se"...) e já sem acentos, como o normalizador deixa.
	private static final Set<String> ENGLISH = Set.of("the", "and", "with", "for", "you", "your", "our", "we", "are",
			"is", "will", "of", "in", "on", "an", "at", "be", "or", "this", "that", "from", "have", "has", "who",
			"what", "about", "their", "they", "us", "can", "would", "should", "into", "within");

	private static final Set<String> PORTUGUESE = Set.of("de", "da", "das", "dos", "que", "para", "com", "em", "uma",
			"um", "os", "nao", "voce", "sao", "nossa", "nosso", "nossos", "mais", "como", "por", "ou", "na", "nas",
			"nos", "pelo", "pela", "ser", "sua", "seu", "seus", "suas", "ao", "aos", "sobre", "tambem", "entre", "esta",
			"estamos", "sera");

	/** Palavras funcionais necessárias para arriscar o idioma de uma descrição. */
	private static final int MIN_HITS = 8;

	/** Palavras de título de vaga que só existem em inglês. */
	private static final Set<String> ENGLISH_TITLE = Set.of("engineer", "engineering", "developer", "development",
			"senior", "staff", "lead", "principal", "mid", "level", "intermediate", "junior", "associate", "specialist",
			"founding", "remote", "work", "team", "contractor", "analyst", "builder", "coding", "jobs", "integration",
			"solutions", "assisted", "deployed", "forward", "hybrid", "tools", "services");

	/** Palavras de título que denunciam uma vaga escrita em português. */
	private static final Set<String> PORTUGUESE_TITLE = Set.of("desenvolvedor", "desenvolvedora", "desenvolvedores",
			"desenvolvimento", "desenvolver", "engenheiro", "engenheira", "engenharia", "analista", "especialista",
			"programador", "programadora", "pessoa", "pleno", "pl", "jr", "remoto", "remota", "presencial", "hibrido",
			"vaga", "afirmativa", "foco", "sistemas", "dados", "de", "do", "da", "dos", "das", "em", "e", "para", "com");

	// "Sênior", "Integração", "Inteligência": acentos que o inglês não tem.
	private static final Pattern PORTUGUESE_ACCENTS = Pattern.compile("[ãõçêâáéíóú]",
			Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

	private static final Pattern WORK_MODEL_SUFFIX = Pattern
		.compile("\\((remoto|remote|h[ií]brido|hybrid|presencial|on-site)\\)", Pattern.CASE_INSENSITIVE);

	// "Cidade, UF" é como o LinkedIn escreve as cidades brasileiras.
	private static final Pattern BRAZILIAN_STATE_CODE = Pattern
		.compile(",\\s*(AC|AL|AP|AM|BA|CE|DF|ES|GO|MA|MT|MS|MG|PA|PB|PR|PE|PI|RJ|RN|RS|RO|RR|SC|SP|SE|TO)\\b");

	private static final List<String> BRAZIL = List.of("brasil", "brazil", "acre", "alagoas", "amapa", "amazonas",
			"bahia", "ceara", "distrito federal", "espirito santo", "goias", "maranhao", "mato grosso", "minas gerais",
			"para", "paraiba", "parana", "pernambuco", "piaui", "rio de janeiro", "rio grande do norte",
			"rio grande do sul", "rondonia", "roraima", "santa catarina", "sao paulo", "sergipe", "tocantins",
			"belo horizonte", "porto alegre", "curitiba", "recife", "salvador", "fortaleza", "brasilia", "campinas",
			"florianopolis", "goiania", "manaus", "belem", "vitoria", "natal", "joao pessoa", "maceio", "aracaju",
			"teresina", "sao luis", "cuiaba", "campo grande", "londrina", "joinville", "ribeirao preto", "sorocaba",
			"santos", "sao jose dos campos", "uberlandia", "juiz de fora", "caxias do sul", "blumenau", "maringa");

	// Países e cidades que aparecem nos alertas e nas fontes internacionais, em português, inglês e espanhol.
	private static final List<String> ABROAD = List.of("argentina", "chile", "colombia", "peru", "paraguai", "paraguay",
			"uruguai", "uruguay", "bolivia", "equador", "ecuador", "venezuela", "mexico", "costa rica", "panama",
			"guatemala", "el salvador", "honduras", "nicaragua", "republica dominicana", "porto rico", "puerto rico",
			"estados unidos", "united states", "usa", "eua", "canada", "portugal", "espanha", "spain", "reino unido",
			"united kingdom", "uk", "inglaterra", "england", "irlanda", "ireland", "alemanha", "germany", "franca",
			"france", "holanda", "paises baixos", "netherlands", "belgica", "belgium", "italia", "italy", "suica",
			"switzerland", "austria", "polonia", "poland", "suecia", "sweden", "noruega", "norway", "dinamarca",
			"denmark", "finlandia", "finland", "estonia", "romenia", "romania", "india", "japao", "japan", "china",
			"singapura", "singapore", "australia", "nova zelandia", "new zealand", "israel", "emirados arabes",
			"africa do sul", "south africa", "europa", "europe", "worldwide", "santiago", "buenos aires", "cordoba",
			"rosario", "bogota", "medellin", "cali", "barranquilla", "cartagena", "bucaramanga", "villavicencio",
			"lima", "montevideu", "montevideo", "assuncao", "asuncion", "ciudad del este", "cidade do mexico",
			"mexico city", "ciudad de mexico", "guadalajara", "monterrey", "quito", "guayaquil", "san jose", "lisboa",
			"lisbon", "porto", "madrid", "barcelona", "londres", "london", "dublin", "berlim", "berlin", "amsterda",
			"amsterdam", "paris", "toronto", "vancouver", "montreal", "nova york", "new york", "miami", "austin",
			"san francisco");

	// Elegibilidade: termos já normalizados (sem acentos, caixa baixa, pontuação trocada por espaço).
	private static final List<String> BRAZIL_ALLOWED = List.of("brazil", "brasil", "brazilian", "brasileiro",
			"brasileira", "brazil based");

	private static final List<String> LATAM_ALLOWED = List.of("latam", "lat am", "latin america", "latin american",
			"america latina", "south america", "america do sul", "americas", "nearshore");

	private static final List<String> RESTRICTED = List.of("us only", "usa only", "u s only", "united states only",
			"us based only", "eu only", "europe only", "uk only", "canada only", "us residents", "us citizens",
			"us citizen", "us citizenship", "green card", "security clearance", "authorized to work in the us",
			"authorized to work in the united states", "eligible to work in the us",
			"eligible to work in the united states", "legally authorized to work", "work authorization in",
			"right to work in", "must be located in", "must be based in", "must reside in", "must live in",
			"based in the us", "based in the united states", "based in europe", "based in the uk", "residing in the us",
			"anywhere in the us", "anywhere in the united states", "within the us", "within the united states",
			"within the eu", "within europe", "within the uk", "remote us", "remote usa", "us remote",
			"remote united states", "emea");

	private static final List<String> GLOBAL_ALLOWED = List.of("worldwide", "anywhere in the world",
			"work from anywhere", "remote anywhere", "from anywhere", "globally remote", "global remote", "remote global",
			"fully remote global", "any country", "any location", "location independent", "qualquer lugar do mundo",
			"de qualquer lugar");

	private static final List<String> CONTRACTOR = List.of("contractor", "contract position", "contract role",
			"contract basis", "contract to hire", "independent contract", "b2b contract", "1099", "freelance",
			"freelancer");

	// "UTC-3", "GMT+1": sinal e número, antes da normalização (que apaga o sinal).
	private static final Pattern UTC_OFFSET = Pattern.compile("\\b(UTC|GMT)\\s?[+\\-−–]\\s?\\d{1,2}(:\\d{2})?",
			Pattern.CASE_INSENSITIVE);

	// Siglas só em maiúsculas ("est" é palavra comum); "ET", "CT" e "PT" ficam de fora, ambíguas demais.
	private static final Pattern ZONE_ABBREVIATION = Pattern
		.compile("\\b(EST|EDT|CST|CDT|MST|MDT|PST|PDT|CET|CEST|BST|GMT|UTC|BRT)\\b");

	private static final Pattern NAMED_ZONE = Pattern.compile(
			"\\b((eastern|pacific|central|mountain|european|brasilia|brazilian)( standard)? time( zones?)?"
					+ "|(us|u\\.s\\.|american|north american|european|latam) time ?zones?)\\b",
			Pattern.CASE_INSENSITIVE);

	private JobScopeDetector() {
	}

	public static JobScope detect(@Nullable String title, @Nullable String location, @Nullable String description) {
		if (isAbroad(location)) {
			return JobScope.INTERNACIONAL;
		}
		Language fromDescription = descriptionLanguage(description);
		if (fromDescription != Language.UNKNOWN) {
			return (fromDescription == Language.ENGLISH) ? JobScope.INTERNACIONAL : JobScope.NACIONAL;
		}
		return isEnglishTitle(title) ? JobScope.INTERNACIONAL : JobScope.NACIONAL;
	}

	/** Uma localidade reconhecida como brasileira nunca é exterior, mesmo com nome de país ("Montenegro, RS"). */
	static boolean isAbroad(@Nullable String location) {
		if (location == null || location.isBlank()) {
			return false;
		}
		String place = WORK_MODEL_SUFFIX.matcher(location).replaceAll(" ");
		if (BRAZILIAN_STATE_CODE.matcher(place).find()) {
			return false;
		}
		String normalized = " " + TextNormalizer.normalize(place) + " ";
		if (BRAZIL.stream().anyMatch((name) -> normalized.contains(" " + name + " "))) {
			return false;
		}
		return ABROAD.stream().anyMatch((name) -> normalized.contains(" " + name + " "));
	}

	/**
	 * Condições de uma vaga do exterior, lidas no título, na localidade e na descrição. A elegibilidade segue esta
	 * ordem: o Brasil citado, a América Latina citada, uma restrição de país ou região ("US only", "authorized to
	 * work in the US", "within the EU"), o remoto global e, sem nada disso, uma localidade num país estrangeiro conta
	 * como restrita a ele.
	 */
	public static InternationalTerms international(@Nullable String title, @Nullable String location,
			@Nullable String description) {
		String raw = String.join(" ", nullToEmpty(title), nullToEmpty(location), nullToEmpty(description));
		String text = " " + TextNormalizer.normalize(raw) + " ";
		InternationalEligibility eligibility;
		if (containsAny(text, BRAZIL_ALLOWED) || (location != null && !isAbroad(location) && isBrazilian(location))) {
			eligibility = InternationalEligibility.BRASIL;
		}
		else if (containsAny(text, LATAM_ALLOWED)) {
			eligibility = InternationalEligibility.LATAM;
		}
		else if (containsAny(text, RESTRICTED)) {
			eligibility = InternationalEligibility.PAIS_ESPECIFICO;
		}
		else if (containsAny(text, GLOBAL_ALLOWED)) {
			eligibility = InternationalEligibility.GLOBAL;
		}
		else if (isAbroad(location)) {
			eligibility = InternationalEligibility.PAIS_ESPECIFICO;
		}
		else {
			eligibility = InternationalEligibility.NAO_INFORMADO;
		}
		return new InternationalTerms(eligibility, timezone(raw), containsAny(text, CONTRACTOR));
	}

	private static @Nullable String timezone(String text) {
		for (Pattern pattern : List.of(UTC_OFFSET, NAMED_ZONE, ZONE_ABBREVIATION)) {
			Matcher matcher = pattern.matcher(text);
			if (matcher.find()) {
				String found = matcher.group().replaceAll("\\s+", " ").strip();
				return (found.length() <= 60) ? found : found.substring(0, 60);
			}
		}
		return null;
	}

	private static boolean isBrazilian(String location) {
		if (BRAZILIAN_STATE_CODE.matcher(location).find()) {
			return true;
		}
		String normalized = " " + TextNormalizer.normalize(location) + " ";
		return BRAZIL.stream().filter(name -> !name.equals("para")).anyMatch(name -> normalized.contains(" " + name + " "));
	}

	private static boolean containsAny(String text, List<String> terms) {
		return terms.stream().anyMatch(term -> text.contains(" " + term + " "));
	}

	private static String nullToEmpty(@Nullable String value) {
		return (value == null) ? "" : value;
	}

	private static Language descriptionLanguage(@Nullable String description) {
		int english = 0;
		int portuguese = 0;
		for (String word : TextNormalizer.normalize(description).split(" ")) {
			if (ENGLISH.contains(word)) {
				english++;
			}
			else if (PORTUGUESE.contains(word)) {
				portuguese++;
			}
		}
		if (english + portuguese < MIN_HITS) {
			return Language.UNKNOWN;
		}
		// Bilíngue ou com folga pequena fica como português.
		return (english > 2 * portuguese) ? Language.ENGLISH : Language.PORTUGUESE;
	}

	static boolean isEnglishTitle(@Nullable String title) {
		if (title == null || title.isBlank() || PORTUGUESE_ACCENTS.matcher(title).find()) {
			return false;
		}
		// "(Remote, Rio de Janeiro)": o "de" do nome da cidade não faz do título um título em português.
		String normalized = " " + TextNormalizer.normalize(title) + " ";
		for (String place : BRAZIL) {
			normalized = normalized.replace(" " + place + " ", " ");
		}
		List<String> words = Arrays.asList(normalized.strip().split(" "));
		return words.stream().anyMatch(ENGLISH_TITLE::contains) && words.stream().noneMatch(PORTUGUESE_TITLE::contains);
	}

	/**
	 * Condições de uma vaga do exterior.
	 * @param timezone fuso exigido como aparece no texto ("EST", "UTC-3", "US time zones"), ou nulo
	 * @param contractor contratação como contractor (sem vínculo de emprego)
	 */
	public record InternationalTerms(InternationalEligibility eligibility, @Nullable String timezone,
			boolean contractor) {
	}

	private enum Language {

		ENGLISH, PORTUGUESE, UNKNOWN

	}

}
