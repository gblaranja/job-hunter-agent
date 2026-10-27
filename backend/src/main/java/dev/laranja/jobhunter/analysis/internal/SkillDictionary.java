package dev.laranja.jobhunter.analysis.internal;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
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
 * Dicionário de skills: detecta tecnologias num texto e normaliza nomes vindos do LLM para a mesma grafia, para que
 * "springboot" e "Spring Boot" contem como a mesma skill. Junta duas fontes: o arquivo {@code analysis/skills.txt},
 * versionado, e as skills aceitas na revisão das sugestões (tabela {@code skill_entry}), que complementam o arquivo
 * sem sobrescrevê-lo.
 */
@Component
public class SkillDictionary {

	private final Map<String, List<String>> fileEntries;

	private volatile Map<String, String> canonicalByAlias;

	SkillDictionary() {
		this(load());
	}

	SkillDictionary(Map<String, List<String>> entries) {
		this.fileEntries = Collections.unmodifiableMap(new LinkedHashMap<>(entries));
		this.canonicalByAlias = index(entries, Map.of());
	}

	/** Troca as skills aceitas na revisão; as do arquivo continuam valendo e ganham num apelido repetido. */
	public void replaceReviewed(Map<String, List<String>> reviewed) {
		this.canonicalByAlias = index(this.fileEntries, reviewed);
	}

	/** Se o nome (ou um apelido) já está no dicionário. */
	public boolean isKnown(String skill) {
		return this.canonicalByAlias.containsKey(TextNormalizer.normalize(skill));
	}

	private static Map<String, String> index(Map<String, List<String>> file, Map<String, List<String>> reviewed) {
		Map<String, String> index = new LinkedHashMap<>();
		file.forEach((canonical, aliases) -> {
			// Nomes curtos e ambíguos ("Go") só contam pelos apelidos explícitos ("golang").
			if (canonical.length() > 2) {
				index.put(TextNormalizer.normalize(canonical), canonical);
			}
			aliases.forEach(alias -> index.putIfAbsent(TextNormalizer.normalize(alias), canonical));
		});
		reviewed.forEach((canonical, aliases) -> {
			if (canonical.length() > 2) {
				index.putIfAbsent(TextNormalizer.normalize(canonical), canonical);
			}
			aliases.forEach(alias -> index.putIfAbsent(TextNormalizer.normalize(alias), canonical));
		});
		return Collections.unmodifiableMap(index);
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
		Map<String, String> aliasIndex = this.canonicalByAlias;
		List<String> aliases = new ArrayList<>(aliasIndex.keySet());
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
					found.putIfAbsent(start, aliasIndex.get(alias));
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
