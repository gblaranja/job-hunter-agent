package dev.laranja.jobhunter.analysis.internal;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;

import dev.laranja.jobhunter.shared.TextNormalizer;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/**
 * Dicionário de skills: detecta tecnologias num texto e normaliza nomes vindos do LLM para a mesma grafia, para que
 * "springboot" e "Spring Boot" contem como a mesma skill. Junta duas fontes: o arquivo {@code analysis/skills.txt},
 * versionado, e as skills aceitas na revisão das sugestões (tabela {@code skill_entry}), que complementam o arquivo
 * sem sobrescrevê-lo. Também guarda as relações entre skills: equivalências parciais (Kafka e RabbitMQ) e
 * implicações (Spring Boot implica Java).
 */
@Component
public class SkillDictionary {

	private final Map<String, List<String>> fileEntries;

	private volatile Map<String, String> canonicalByAlias;

	/** Skill (em caixa baixa) e as que ela implica, já com a grafia canônica. */
	private final Map<String, List<String>> implications = new LinkedHashMap<>();

	/** Skill (em caixa baixa) e as equivalentes parciais, na ordem do arquivo. */
	private final Map<String, Set<String>> partials = new LinkedHashMap<>();

	SkillDictionary() {
		this(load());
	}

	private SkillDictionary(Loaded loaded) {
		this(loaded.entries());
		loaded.implications().forEach(this::addImplication);
		loaded.partials().forEach(this::addPartialGroup);
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

	/**
	 * As skills do currículo mais as que elas implicam (transitivamente): quem tem Spring Boot também tem Spring e
	 * Java.
	 */
	public List<String> withImplied(Collection<String> skills) {
		Set<String> seen = new LinkedHashSet<>();
		List<String> result = new ArrayList<>();
		ArrayDeque<String> pending = new ArrayDeque<>(skills);
		while (!pending.isEmpty()) {
			String skill = pending.poll();
			if (skill == null || skill.isBlank() || !seen.add(skill.toLowerCase(Locale.ROOT))) {
				continue;
			}
			result.add(skill);
			pending.addAll(this.implications.getOrDefault(canonical(skill).toLowerCase(Locale.ROOT), List.of()));
		}
		return result;
	}

	/**
	 * Uma skill do currículo que equivale em parte à pedida (RabbitMQ para Kafka); vazio se não houver.
	 *
	 * @param owned skills do currículo em caixa baixa
	 */
	public Optional<String> partialMatch(String required, Map<String, String> owned) {
		for (String equivalent : this.partials.getOrDefault(canonical(required).toLowerCase(Locale.ROOT), Set.of())) {
			String ownedName = owned.get(equivalent.toLowerCase(Locale.ROOT));
			if (ownedName != null) {
				return Optional.of(ownedName);
			}
		}
		return Optional.empty();
	}

	void addImplication(String skill, List<String> implied) {
		this.implications.computeIfAbsent(canonical(skill).toLowerCase(Locale.ROOT), key -> new ArrayList<>())
			.addAll(implied.stream().map(this::canonical).toList());
	}

	/** Todas as skills do grupo equivalem em parte umas às outras. */
	void addPartialGroup(List<String> group) {
		List<String> names = group.stream().map(this::canonical).toList();
		for (String skill : names) {
			Set<String> others = this.partials.computeIfAbsent(skill.toLowerCase(Locale.ROOT), key -> new LinkedHashSet<>());
			names.stream().filter(other -> !other.equalsIgnoreCase(skill)).forEach(others::add);
		}
	}

	private static Loaded load() {
		try {
			String content = new ClassPathResource("analysis/skills.txt").getContentAsString(StandardCharsets.UTF_8);
			Map<String, List<String>> entries = new LinkedHashMap<>();
			Map<String, List<String>> implications = new LinkedHashMap<>();
			List<List<String>> partials = new ArrayList<>();
			for (String line : content.split("\n")) {
				String trimmed = line.strip();
				if ((trimmed.startsWith("~") || trimmed.startsWith(">")) && trimmed.contains("=")) {
					String left = trimmed.substring(1, trimmed.indexOf('=')).strip();
					List<String> right = splitNames(trimmed.substring(trimmed.indexOf('=') + 1));
					if (trimmed.startsWith(">")) {
						implications.computeIfAbsent(left, key -> new ArrayList<>()).addAll(right);
					}
					else {
						List<String> group = new ArrayList<>(List.of(left));
						group.addAll(right);
						partials.add(group);
					}
					continue;
				}
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
			return new Loaded(entries, implications, partials);
		}
		catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
	}

	private static List<String> splitNames(String names) {
		return List.of(names.split(",")).stream().map(String::strip).filter(name -> !name.isEmpty()).toList();
	}

	private record Loaded(Map<String, List<String>> entries, Map<String, List<String>> implications,
			List<List<String>> partials) {
	}

}
