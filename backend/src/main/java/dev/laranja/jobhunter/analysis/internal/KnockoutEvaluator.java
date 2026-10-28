package dev.laranja.jobhunter.analysis.internal;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import dev.laranja.jobhunter.analysis.JobKnockout;
import dev.laranja.jobhunter.analysis.KnockoutStatus;
import dev.laranja.jobhunter.analysis.KnockoutType;
import dev.laranja.jobhunter.profile.CandidateProfileView;
import dev.laranja.jobhunter.profile.EducationLevel;
import dev.laranja.jobhunter.profile.EnglishLevel;
import dev.laranja.jobhunter.shared.TextNormalizer;
import org.jspecify.annotations.Nullable;

/**
 * Compara os eliminatórios da vaga com os dados da pessoa: ATENDE, NAO_ATENDE ou SEM_INFORMACAO. Enquanto os dados
 * da triagem não foram salvos, tudo fica sem informação (e o score não é limitado).
 */
final class KnockoutEvaluator {

	private static final Map<String, String> STATES = Map.ofEntries(Map.entry("AC", "acre"),
			Map.entry("AL", "alagoas"), Map.entry("AP", "amapa"), Map.entry("AM", "amazonas"), Map.entry("BA", "bahia"),
			Map.entry("CE", "ceara"), Map.entry("DF", "distrito federal"), Map.entry("ES", "espirito santo"),
			Map.entry("GO", "goias"), Map.entry("MA", "maranhao"), Map.entry("MT", "mato grosso"),
			Map.entry("MS", "mato grosso do sul"), Map.entry("MG", "minas gerais"), Map.entry("PA", "para"),
			Map.entry("PB", "paraiba"), Map.entry("PR", "parana"), Map.entry("PE", "pernambuco"),
			Map.entry("PI", "piaui"), Map.entry("RJ", "rio de janeiro"), Map.entry("RN", "rio grande do norte"),
			Map.entry("RS", "rio grande do sul"), Map.entry("RO", "rondonia"), Map.entry("RR", "roraima"),
			Map.entry("SC", "santa catarina"), Map.entry("SP", "sao paulo"), Map.entry("SE", "sergipe"),
			Map.entry("TO", "tocantins"));

	private static final Map<String, String> COUNTRY_ALIASES = Map.of("USA", "US", "EUA", "US", "UK", "GB", "BRASIL",
			"BR", "BRAZIL", "BR");

	private KnockoutEvaluator() {
	}

	static List<JobKnockout> evaluate(List<KnockoutRequirement> requirements, CandidateProfileView profile) {
		List<JobKnockout> result = new ArrayList<>();
		for (KnockoutRequirement requirement : requirements) {
			Optional<KnockoutType> type = parseType(requirement.type());
			if (type.isEmpty() || requirement.value() == null || requirement.value().isBlank()) {
				continue;
			}
			String text = (requirement.text() == null) ? requirement.value() : requirement.text();
			Verdict verdict = (profile.updatedAt() == null)
					? new Verdict(KnockoutStatus.SEM_INFORMACAO, "Preencha seus dados da triagem em Configurações.")
					: judge(type.get(), requirement.value().strip(), profile);
			result.add(new JobKnockout(type.get(), text, requirement.value().strip(), verdict.status(), verdict.reason()));
		}
		return result;
	}

	static boolean anyFailed(List<JobKnockout> knockouts) {
		return knockouts.stream().anyMatch(knockout -> knockout.status() == KnockoutStatus.NAO_ATENDE);
	}

	private static Verdict judge(KnockoutType type, String value, CandidateProfileView profile) {
		return switch (type) {
			case ANOS_EXPERIENCIA -> years(value, profile);
			case IDIOMA -> language(value, profile);
			case LOCALIDADE -> location(value, profile);
			case PRESENCIAL -> onsite(value, profile);
			case AUTORIZACAO_TRABALHO -> authorization(value, profile);
			case FUSO -> timezone(value);
			case FORMACAO -> education(value, profile);
			case CERTIFICACAO -> certification(value, profile);
			case CONTRATO -> contract(value, profile);
		};
	}

	private static Verdict years(String value, CandidateProfileView profile) {
		Integer required = number(value);
		if (required == null) {
			return unknown("Não deu para ler quantos anos a vaga pede.");
		}
		if (profile.yearsOfExperience() == null) {
			return unknown("Informe seus anos de experiência em Configurações.");
		}
		String reason = "Você tem " + profile.yearsOfExperience() + " anos; a vaga pede " + required + ".";
		return verdict(profile.yearsOfExperience() >= required, reason);
	}

	private static Verdict language(String value, CandidateProfileView profile) {
		String[] parts = value.toUpperCase(Locale.ROOT).split(":");
		String language = TextNormalizer.normalize(parts[0]);
		if (!language.equals("ingles") && !language.equals("english")) {
			return unknown("Só o nível de inglês é comparado; confira o de " + parts[0].toLowerCase(Locale.ROOT) + ".");
		}
		Optional<EnglishLevel> required = (parts.length > 1) ? ResumeHints.parseEnglish(parts[1]) : Optional.empty();
		if (profile.englishLevel() == null) {
			return unknown("Informe seu nível de inglês em Configurações.");
		}
		if (required.isEmpty()) {
			return unknown("A vaga pede inglês sem dizer o nível.");
		}
		String reason = "Seu inglês: " + label(profile.englishLevel()) + "; a vaga pede " + label(required.get()) + ".";
		return verdict(profile.englishLevel().ordinal() >= required.get().ordinal(), reason);
	}

	private static Verdict location(String value, CandidateProfileView profile) {
		String target = " " + TextNormalizer.normalize(value) + " ";
		if (target.matches(".*( remot| anywhere| qualquer lugar).*")) {
			return new Verdict(KnockoutStatus.ATENDE, "A vaga aceita trabalho remoto.");
		}
		if (target.matches(".*( brasil | brazil | br ).*") && target.strip().split(" ").length <= 2) {
			return verdict(profile.workAuthorizations().contains("BR"), "A vaga pede alguém no Brasil.");
		}
		if (profile.city() == null && profile.state() == null) {
			return unknown("Informe sua cidade e UF em Configurações.");
		}
		String where = (profile.city() != null ? profile.city() : "") + (profile.state() != null ? "/" + profile.state() : "");
		String city = (profile.city() == null) ? null : TextNormalizer.normalize(profile.city());
		if (city != null && target.contains(" " + city + " ")) {
			return new Verdict(KnockoutStatus.ATENDE, "Você está em " + where + ".");
		}
		String state = (profile.state() == null) ? null : profile.state().toUpperCase(Locale.ROOT);
		boolean stateCode = state != null && target.contains(" " + state.toLowerCase(Locale.ROOT) + " ");
		boolean stateName = state != null && STATES.containsKey(state) && target.contains(" " + STATES.get(state) + " ");
		if (stateCode && target.strip().length() == 2) {
			return new Verdict(KnockoutStatus.ATENDE, "Você está em " + where + ".");
		}
		if (stateCode || stateName) {
			return unknown("Mesmo estado (" + state + "); confira se a cidade é perto de você.");
		}
		return new Verdict(KnockoutStatus.NAO_ATENDE, "Você está em " + where + ".");
	}

	private static Verdict onsite(String value, CandidateProfileView profile) {
		Integer days = number(value);
		if (days == null) {
			return unknown("Não deu para ler quantos dias presenciais a vaga pede.");
		}
		if (profile.maxOnsiteDays() == null) {
			return unknown("Informe quantos dias presenciais você aceita em Configurações.");
		}
		String reason = "Você aceita até " + profile.maxOnsiteDays() + " dia(s) no escritório; a vaga pede " + days + ".";
		return verdict(days <= profile.maxOnsiteDays(), reason);
	}

	private static Verdict authorization(String value, CandidateProfileView profile) {
		String country = value.toUpperCase(Locale.ROOT).strip();
		country = COUNTRY_ALIASES.getOrDefault(country, country);
		if (profile.workAuthorizations().isEmpty()) {
			return unknown("Informe onde você pode trabalhar em Configurações.");
		}
		boolean ok = profile.workAuthorizations().contains(country);
		return verdict(ok, ok ? "Você pode trabalhar em " + country + "."
				: "Você não informou autorização para trabalhar em " + country + ".");
	}

	private static Verdict timezone(String value) {
		String normalized = TextNormalizer.normalize(value);
		if (normalized.matches(".*(utc 3|gmt 3|brt|brasilia|sao paulo).*")) {
			return new Verdict(KnockoutStatus.ATENDE, "É o horário de Brasília.");
		}
		return unknown("Confira se o horário pedido é compatível com o seu.");
	}

	private static Verdict education(String value, CandidateProfileView profile) {
		Optional<EducationLevel> required = parseEducation(value);
		if (required.isEmpty()) {
			return unknown("Não deu para ler a formação pedida.");
		}
		if (profile.educationLevel() == null) {
			return unknown("Informe sua formação em Configurações.");
		}
		return verdict(profile.educationLevel().ordinal() >= required.get().ordinal(),
				"Sua formação: " + profile.educationLevel().name().toLowerCase(Locale.ROOT).replace('_', ' ') + ".");
	}

	private static Verdict certification(String value, CandidateProfileView profile) {
		String required = TextNormalizer.normalize(value);
		boolean has = profile.certifications().stream()
			.map(TextNormalizer::normalize)
			.anyMatch(owned -> !owned.isEmpty() && (owned.contains(required) || required.contains(owned)));
		return verdict(has, has ? "Está entre as suas certificações." : "Não está entre as suas certificações.");
	}

	private static Verdict contract(String value, CandidateProfileView profile) {
		String regime = value.toUpperCase(Locale.ROOT).strip();
		if (profile.acceptedContracts().isEmpty()) {
			return unknown("Informe os regimes que você aceita em Configurações.");
		}
		boolean ok = profile.acceptedContracts().contains(regime);
		return verdict(ok, ok ? "Você aceita " + regime + "." : "Você não aceita " + regime + ".");
	}

	static Optional<KnockoutType> parseType(@Nullable String type) {
		if (type == null) {
			return Optional.empty();
		}
		try {
			return Optional.of(KnockoutType.valueOf(type.strip().toUpperCase(Locale.ROOT)));
		}
		catch (IllegalArgumentException ex) {
			return Optional.empty();
		}
	}

	private static Optional<EducationLevel> parseEducation(String value) {
		String normalized = TextNormalizer.normalize(value).toUpperCase(Locale.ROOT).replace(' ', '_');
		for (EducationLevel level : EducationLevel.values()) {
			if (level.name().equals(normalized)) {
				return Optional.of(level);
			}
		}
		if (normalized.contains("DOUTORADO") || normalized.contains("PHD")) {
			return Optional.of(EducationLevel.DOUTORADO);
		}
		if (normalized.contains("MESTRADO") || normalized.contains("MASTER")) {
			return Optional.of(EducationLevel.MESTRADO);
		}
		if (normalized.contains("SUPERIOR") || normalized.contains("BACHAREL") || normalized.contains("GRADUA")
				|| normalized.contains("BACHELOR")) {
			return Optional.of(EducationLevel.SUPERIOR);
		}
		return Optional.empty();
	}

	private static @Nullable Integer number(String value) {
		String digits = value.replaceAll("\\D+", " ").strip();
		if (digits.isEmpty()) {
			return null;
		}
		return Integer.parseInt(digits.split(" ")[0]);
	}

	private static String label(EnglishLevel level) {
		return switch (level) {
			case NENHUM -> "nenhum";
			case BASICO -> "básico";
			case INTERMEDIARIO -> "intermediário";
			case AVANCADO -> "avançado";
			case FLUENTE -> "fluente";
		};
	}

	private static Verdict verdict(boolean ok, String reason) {
		return new Verdict(ok ? KnockoutStatus.ATENDE : KnockoutStatus.NAO_ATENDE, reason);
	}

	private static Verdict unknown(String reason) {
		return new Verdict(KnockoutStatus.SEM_INFORMACAO, reason);
	}

	private record Verdict(KnockoutStatus status, String reason) {
	}

}
