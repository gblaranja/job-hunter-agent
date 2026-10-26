package dev.laranja.jobhunter.interviews;

import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import dev.laranja.jobhunter.interviews.internal.StoryPolisher;
import dev.laranja.jobhunter.shared.BusinessRuleException;
import dev.laranja.jobhunter.shared.NotFoundException;
import dev.laranja.jobhunter.shared.jdbc.JdbcRows;
import org.jspecify.annotations.Nullable;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Banco de histórias STAR. O texto é sempre seu; a IA só sugere uma versão lapidada, que você aceita ou não. */
@Service
@Transactional
public class StarStories {

	private final JdbcClient jdbc;

	private final StoryPolisher polisher;

	private final Clock clock;

	StarStories(JdbcClient jdbc, StoryPolisher polisher, Clock clock) {
		this.jdbc = jdbc;
		this.polisher = polisher;
		this.clock = clock;
	}

	public StarStoryView create(StarStoryCommand command) {
		Instant now = this.clock.instant();
		long id = this.jdbc.sql("""
				insert into star_story (title, situation, task, action, result, reflection, skills, created_at, updated_at)
				values (:title, :situation, :task, :action, :result, :reflection, :skills, :now, :now)
				returning id
				""")
			.param("title", command.title().strip())
			.param("situation", text(command.situation()))
			.param("task", text(command.task()))
			.param("action", text(command.action()))
			.param("result", text(command.result()))
			.param("reflection", blankToNull(command.reflection()))
			.param("skills", skills(command.skills()))
			.param("now", Timestamp.from(now))
			.query(Long.class)
			.single();
		return get(id);
	}

	public StarStoryView update(long id, StarStoryCommand command) {
		int updated = this.jdbc.sql("""
				update star_story set title = :title, situation = :situation, task = :task, action = :action,
				    result = :result, reflection = :reflection, skills = :skills, updated_at = :now
				where id = :id
				""")
			.param("id", id)
			.param("title", command.title().strip())
			.param("situation", text(command.situation()))
			.param("task", text(command.task()))
			.param("action", text(command.action()))
			.param("result", text(command.result()))
			.param("reflection", blankToNull(command.reflection()))
			.param("skills", skills(command.skills()))
			.param("now", Timestamp.from(this.clock.instant()))
			.update();
		if (updated == 0) {
			throw NotFoundException.of("História", id);
		}
		return get(id);
	}

	public void delete(long id) {
		if (this.jdbc.sql("delete from star_story where id = :id").param("id", id).update() == 0) {
			throw NotFoundException.of("História", id);
		}
	}

	@Transactional(readOnly = true)
	public StarStoryView get(long id) {
		return this.jdbc.sql("select * from star_story where id = :id")
			.param("id", id)
			.query((rs, row) -> toView(rs))
			.optional()
			.orElseThrow(() -> NotFoundException.of("História", id));
	}

	@Transactional(readOnly = true)
	public List<StarStoryView> list() {
		return this.jdbc.sql("select * from star_story order by updated_at desc, id desc")
			.query((rs, row) -> toView(rs))
			.list();
	}

	/**
	 * Sugere uma versão mais clara do texto informado (salvo ou não), sem fatos novos. Roda fora de transação para
	 * não segurar a conexão durante a chamada à IA.
	 */
	@Transactional(propagation = Propagation.NOT_SUPPORTED)
	public StarStoryPolish polish(StarStoryCommand command) {
		if (!this.polisher.enabled()) {
			throw new BusinessRuleException(
					"Lapidar a história usa a Claude API: configure JOBHUNTER_AI_API_KEY para habilitar.");
		}
		if (text(command.situation()).isEmpty() && text(command.action()).isEmpty() && text(command.result()).isEmpty()) {
			throw new BusinessRuleException("Escreva a história antes de pedir para lapidar.");
		}
		return this.polisher.polish(command);
	}

	private static StarStoryView toView(ResultSet rs) throws SQLException {
		return new StarStoryView(rs.getLong("id"), rs.getString("title"), rs.getString("situation"),
				rs.getString("task"), rs.getString("action"), rs.getString("result"), rs.getString("reflection"),
				array(rs.getArray("skills")), JdbcRows.instant(rs, "created_at"), JdbcRows.instant(rs, "updated_at"));
	}

	static List<String> array(@Nullable Array array) throws SQLException {
		return (array != null) ? Arrays.asList((String[]) array.getArray()) : List.of();
	}

	/** Sem repetir a mesma skill com outra grafia de maiúsculas. */
	private static String[] skills(@Nullable List<String> skills) {
		Map<String, String> unique = new LinkedHashMap<>();
		if (skills != null) {
			skills.stream()
				.filter(skill -> skill != null && !skill.isBlank())
				.map(String::strip)
				.forEach(skill -> unique.putIfAbsent(skill.toLowerCase(Locale.ROOT), skill));
		}
		return unique.values().toArray(String[]::new);
	}

	private static String text(@Nullable String value) {
		return (value == null) ? "" : value.strip();
	}

	private static @Nullable String blankToNull(@Nullable String value) {
		return (value == null || value.isBlank()) ? null : value.strip();
	}

}
