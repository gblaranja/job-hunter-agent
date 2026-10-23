package dev.laranja.jobhunter.analysis.internal;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import dev.laranja.jobhunter.shared.TextNormalizer;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/**
 * Dicionário de skills ({@code analysis/skills.txt}): detecta tecnologias num texto e normaliza nomes vindos do
 * LLM para a mesma grafia, para que "springboot" e "Spring Boot" contem como a mesma skill.
 */
@Component
public class SkillDictionary {

	private final Map<String, String> canonicalByAlias = new LinkedHashMap<>();

	SkillDictionary() {
		this(load());
	}

	SkillDictionary(Map<String, List<String>> entries) {
		entries.forEach((canonical, aliases) -> {
			// Nomes curtos e ambíguos ("Go") só contam pelos apelidos explícitos ("golang").
			if (canonical.length() > 2) {
				this.canonicalByAlias.put(TextNormalizer.normalize(canonical), canonical);
			}
			aliases.forEach(alias -> this.canonicalByAlias.putIfAbsent(TextNormalizer.normalize(alias), canonical));
		});
	}

	/**
	 * Skills do dicionário que aparecem no texto como palavras inteiras, na ordem em que aparecem. Um apelido
	 * dentro de outro mais longo não conta: "Spring Boot" não conta também como "Spring".
	 */
	public List<String> detect(String text) {
		if (text == null || text.isBlank()) {
			return List.of();
		}
		String normalized = " " + TextNormalizer.normalize(text) + " ";
		List<String> aliases = new ArrayList<>(this.canonicalByAlias.keySet());
		aliases.sort((a, b) -> Integer.compare(b.length(), a.length()));
		boolean[] covered = new boolean[normalized.length()];
		TreeMap<Integer, String> found = new TreeMap<>();
		for (String alias : aliases) {
			if (alias.isEmpty()) {
				continue;
			}
			String needle = " " + alias + " ";
			int from = 0;
			int index;
			while ((index = normalized.indexOf(needle, from)) >= 0) {
				int start = index + 1;
				int end = start + alias.length();
				if (!covered[start]) {
					for (int i = start; i < end; i++) {
						covered[i] = true;
					}
					found.putIfAbsent(start, this.canonicalByAlias.get(alias));
				}
				from = index + 1;
			}
		}
		return List.copyOf(new LinkedHashSet<>(found.values()));
	}

	/** Grafia canônica de uma skill; fora do dicionário, mantém o nome como veio, sem espaços sobrando. */
	public String canonical(String skill) {
		String known = this.canonicalByAlias.get(TextNormalizer.normalize(skill));
		return (known != null) ? known : skill.strip().replaceAll("\\s+", " ");
	}

	/** Normaliza e remove duplicatas, preservando a ordem. */
	public List<String> canonicalize(Collection<String> skills) {
		Set<String> result = new LinkedHashSet<>();
		Set<String> seen = new LinkedHashSet<>();
		for (String skill : skills) {
			if (skill == null || skill.isBlank()) {
				continue;
			}
			String canonical = canonical(skill);
			if (seen.add(canonical.toLowerCase(Locale.ROOT))) {
				result.add(canonical);
			}
		}
		return new ArrayList<>(result);
	}

	private static Map<String, List<String>> load() {
		try {
			String content = new ClassPathResource("analysis/skills.txt").getContentAsString(StandardCharsets.UTF_8);
			Map<String, List<String>> entries = new LinkedHashMap<>();
			for (String line : content.split("\n")) {
				String trimmed = line.strip();
				if (trimmed.isEmpty() || trimmed.startsWith("#") || !trimmed.contains(":")) {
					continue;
				}
				String canonical = trimmed.substring(0, trimmed.indexOf(':')).strip();
				List<String> aliases = List.of(trimmed.substring(trimmed.indexOf(':') + 1).split(","))
					.stream()
					.map(String::strip)
					.filter(alias -> !alias.isEmpty())
					.toList();
				entries.put(canonical, aliases);
			}
			return entries;
		}
		catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
	}

}
