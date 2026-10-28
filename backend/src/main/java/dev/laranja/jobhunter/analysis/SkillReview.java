package dev.laranja.jobhunter.analysis;

import java.sql.Array;
import java.sql.Timestamp;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import dev.laranja.jobhunter.analysis.internal.SkillDictionary;
import dev.laranja.jobhunter.shared.ConflictException;
import dev.laranja.jobhunter.shared.NotFoundException;
import dev.laranja.jobhunter.shared.TextNormalizer;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Revisão do dicionário de skills: as skills pedidas nas vagas que o dicionário não conhece viram sugestões, com o
 * número de vagas; aceitar grava a grafia canônica e os apelidos em {@code skill_entry}, que passa a valer junto com
 * o {@code skills.txt} (e as vagas que pedem a skill são repontuadas); ignorar tira a sugestão da fila de vez.
 */
@Service
public class SkillReview implements ApplicationRunner {

	/** A partir de quantas vagas uma skill desconhecida vira sugestão. */
	public static final int DEFAULT_MIN_JOBS = 3;

	private static final Logger log = LoggerFactory.getLogger(SkillReview.class);

	private final SkillDictionary dictionary;

	private final JobAnalyzer analyzer;

	private final JdbcClient jdbc;

	private final Clock clock;

	SkillReview(SkillDictionary dictionary, JobAnalyzer analyzer, JdbcClient jdbc, Clock clock) {
		this.dictionary = dictionary;
		this.analyzer = analyzer;
		this.jdbc = jdbc;
		this.clock = clock;
	}

	/** Na subida, o dicionário passa a incluir as skills já aceitas. */
	@Override
	public void run(ApplicationArguments args) {
		reload();
	}

	/**
	 * Skills pedidas (obrigatórias ou desejáveis) nas análises que não estão no dicionário nem foram ignoradas, da
	 * mais pedida para a menos. Grafias que só mudam caixa, acento ou pontuação contam juntas.
	 */
	@Transactional(readOnly = true)
	public List<SkillSuggestion> suggestions(int minJobs) {
		record Row(String skill, long jobs) {
		}
		List<Row> rows = this.jdbc.sql("""
				select s.skill, count(distinct ja.job_id) as jobs
				from job_analysis ja
				cross join lateral jsonb_array_elements_text(ja.required_skills || ja.nice_to_have) as s(skill)
				group by s.skill
				""").query(Row.class).list();
		Set<String> ignored = new LinkedHashSet<>(this.jdbc.sql("select name from skill_ignored").query(String.class).list());
		Map<String, List<Row>> byName = new LinkedHashMap<>();
		for (Row row : rows) {
			String key = TextNormalizer.normalize(row.skill());
			if (key.isEmpty() || key.length() > 100 || ignored.contains(key) || this.dictionary.isKnown(row.skill())) {
				continue;
			}
			byName.computeIfAbsent(key, k -> new ArrayList<>()).add(row);
		}
		Map<String, Long> jobsByName = jobsByNormalizedName();
		return byName.entrySet().stream().map(entry -> {
			List<Row> spellings = new ArrayList<>(entry.getValue());
			spellings.sort(Comparator.comparingLong(Row::jobs).reversed());
			return new SkillSuggestion(spellings.get(0).skill(), entry.getKey(),
					spellings.stream().map(Row::skill).toList(), jobsByName.getOrDefault(entry.getKey(), 0L));
		})
			.filter(suggestion -> suggestion.jobCount() >= minJobs)
			.sorted(Comparator.comparingLong(SkillSuggestion::jobCount).reversed().thenComparing(SkillSuggestion::name))
			.toList();
	}

	/** Skills aceitas na revisão, da mais recente para a mais antiga. */
	@Transactional(readOnly = true)
	public List<ReviewedSkill> reviewed() {
		return this.jdbc.sql("""
				select id, canonical, aliases, esco_uri, label_pt, label_en, created_at
				from skill_entry order by created_at desc, id desc
				""")
			.query((rs, row) -> new ReviewedSkill(rs.getLong("id"), rs.getString("canonical"), list(rs.getArray("aliases")),
					rs.getString("esco_uri"), rs.getString("label_pt"), rs.getString("label_en"),
					rs.getTimestamp("created_at").toInstant()))
			.list();
	}

	/**
	 * Aceita uma skill: grava a grafia canônica e os apelidos, atualiza o dicionário e repontua as vagas abertas que a
	 * pedem.
	 */
	@Transactional
	public ReviewedSkill accept(SkillAcceptance acceptance) {
		String canonical = acceptance.canonical().strip().replaceAll("\\s+", " ");
		boolean exists = this.jdbc.sql("select count(*) from skill_entry where lower(canonical) = lower(:canonical)")
			.param("canonical", canonical)
			.query(Long.class)
			.single() > 0;
		if (exists) {
			throw new ConflictException("Essa skill já foi aceita.", null);
		}
		List<String> aliases = acceptance.aliasesOrEmpty()
			.stream()
			.map(String::strip)
			.filter(alias -> !alias.isEmpty() && !alias.equalsIgnoreCase(canonical))
			.distinct()
			.toList();
		long id = this.jdbc.sql("""
				insert into skill_entry (canonical, aliases, esco_uri, label_pt, label_en, created_at)
				values (:canonical, string_to_array(:aliases, chr(31)), :escoUri, :labelPt, :labelEn, :now)
				returning id
				""")
			.param("canonical", canonical)
			.param("aliases", aliases.isEmpty() ? null : String.join(String.valueOf((char) 31), aliases))
			.param("escoUri", blankToNull(acceptance.escoUri()))
			.param("labelPt", blankToNull(acceptance.labelPt()))
			.param("labelEn", blankToNull(acceptance.labelEn()))
			.param("now", Timestamp.from(this.clock.instant()))
			.query(Long.class)
			.single();
		this.jdbc.sql("delete from skill_ignored where name = :name").param("name", TextNormalizer.normalize(canonical)).update();
		reload();
		List<String> names = new ArrayList<>(aliases);
		names.add(canonical);
		int rescored = this.analyzer.rescoreJobsMentioning(names);
		log.info("Skill {} aceita no dicionário; {} vagas repontuadas.", canonical, rescored);
		return reviewed().stream().filter(skill -> skill.id() == id).findFirst().orElseThrow();
	}

	/** Não sugere mais esta skill. */
	@Transactional
	public void ignore(String name) {
		String key = TextNormalizer.normalize(name);
		if (key.isEmpty()) {
			return;
		}
		this.jdbc.sql("insert into skill_ignored (name, created_at) values (:name, :now) on conflict (name) do nothing")
			.param("name", key.length() > 100 ? key.substring(0, 100) : key)
			.param("now", Timestamp.from(this.clock.instant()))
			.update();
	}

	/** Tira uma skill aceita do dicionário; ela pode voltar como sugestão. */
	@Transactional
	public void remove(long id) {
		if (this.jdbc.sql("delete from skill_entry where id = :id").param("id", id).update() == 0) {
			throw NotFoundException.of("Skill", id);
		}
		reload();
	}

	private void reload() {
		Map<String, List<String>> entries = new LinkedHashMap<>();
		this.jdbc.sql("select canonical, aliases from skill_entry order by id")
			.query((rs, row) -> entries.put(rs.getString("canonical"), list(rs.getArray("aliases"))))
			.list();
		this.dictionary.replaceReviewed(entries);
	}

	/** Vagas distintas por nome normalizado: a mesma vaga pode trazer duas grafias da mesma skill. */
	private Map<String, Long> jobsByNormalizedName() {
		Map<String, Set<Long>> jobs = new LinkedHashMap<>();
		this.jdbc.sql("""
				select ja.job_id, s.skill
				from job_analysis ja
				cross join lateral jsonb_array_elements_text(ja.required_skills || ja.nice_to_have) as s(skill)
				""")
			.query((rs, row) -> jobs.computeIfAbsent(TextNormalizer.normalize(rs.getString("skill")), k -> new LinkedHashSet<>())
				.add(rs.getLong("job_id")))
			.list();
		Map<String, Long> counts = new LinkedHashMap<>();
		jobs.forEach((name, ids) -> counts.put(name, (long) ids.size()));
		return counts;
	}

	private static List<String> list(@Nullable Array array) throws java.sql.SQLException {
		return (array == null) ? List.of() : Arrays.asList((String[]) array.getArray());
	}

	private static @Nullable String blankToNull(@Nullable String value) {
		return (value == null || value.isBlank()) ? null : value.strip();
	}

	/**
	 * Skill desconhecida pedida nas vagas.
	 * @param name grafia mais comum
	 * @param key nome normalizado, usado para ignorar
	 * @param spellings grafias encontradas, que viram apelidos ao aceitar
	 * @param jobCount vagas que pedem a skill
	 */
	public record SkillSuggestion(String name, String key, List<String> spellings, long jobCount) {
	}

	/** Skill aceita na revisão. */
	public record ReviewedSkill(long id, String canonical, List<String> aliases, @Nullable String escoUri,
			@Nullable String labelPt, @Nullable String labelEn, java.time.Instant createdAt) {
	}

	/**
	 * Aceite de uma sugestão.
	 * @param aliases outras grafias que contam como a mesma skill
	 * @param escoUri URI da skill na ESCO, se consultada
	 */
	public record SkillAcceptance(@NotBlank @Size(max = 100) String canonical, @Nullable List<@Size(max = 100) String> aliases,
			@Nullable @Size(max = 300) String escoUri, @Nullable @Size(max = 200) String labelPt,
			@Nullable @Size(max = 200) String labelEn) {

		List<String> aliasesOrEmpty() {
			return (this.aliases == null) ? List.of() : this.aliases;
		}

	}

}
