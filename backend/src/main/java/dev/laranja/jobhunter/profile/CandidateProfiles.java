package dev.laranja.jobhunter.profile;

import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Clock;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import dev.laranja.jobhunter.shared.jdbc.JdbcRows;
import org.jspecify.annotations.Nullable;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Dados da pessoa candidata usados na triagem (linha única em {@code candidate_profile}). Cada alteração publica
 * {@link CandidateProfileChanged}, para a análise reavaliar os eliminatórios das vagas abertas.
 */
@Service
@Transactional
public class CandidateProfiles {

	private final JdbcClient jdbc;

	private final ApplicationEventPublisher events;

	private final Clock clock;

	CandidateProfiles(JdbcClient jdbc, ApplicationEventPublisher events, Clock clock) {
		this.jdbc = jdbc;
		this.events = events;
		this.clock = clock;
	}

	/** O perfil atual; os padrões enquanto não foi preenchido. */
	@Transactional(readOnly = true)
	public CandidateProfileView current() {
		return this.jdbc.sql("select * from candidate_profile where id = 1")
			.query((rs, row) -> map(rs))
			.optional()
			.orElseGet(CandidateProfileView::empty);
	}

	public CandidateProfileView update(CandidateProfileCommand command) {
		this.jdbc.sql("""
				insert into candidate_profile (id, years_of_experience, english_level, city, state, max_onsite_days,
				                               accepted_contracts, work_authorizations, education_level, education,
				                               certifications, desired_salary_clt, desired_salary_pj, weekly_goal,
				                               updated_at)
				values (1, :years, :english, :city, :state, :onsite, :contracts, :authorizations, :educationLevel,
				        :education, :certifications, :salaryClt, :salaryPj, :weeklyGoal, :now)
				on conflict (id) do update set
				    years_of_experience = excluded.years_of_experience, english_level = excluded.english_level,
				    city = excluded.city, state = excluded.state, max_onsite_days = excluded.max_onsite_days,
				    accepted_contracts = excluded.accepted_contracts, work_authorizations = excluded.work_authorizations,
				    education_level = excluded.education_level, education = excluded.education,
				    certifications = excluded.certifications, desired_salary_clt = excluded.desired_salary_clt,
				    desired_salary_pj = excluded.desired_salary_pj, weekly_goal = excluded.weekly_goal,
				    updated_at = excluded.updated_at
				""")
			.param("years", command.yearsOfExperience(), Types.INTEGER)
			.param("english", name(command.englishLevel()), Types.VARCHAR)
			.param("city", blankToNull(command.city()), Types.VARCHAR)
			.param("state", (blankToNull(command.state()) != null) ? command.state().strip().toUpperCase(Locale.ROOT)
					: null, Types.VARCHAR)
			.param("onsite", command.maxOnsiteDays(), Types.INTEGER)
			.param("contracts", clean(command.acceptedContracts(), true))
			.param("authorizations", clean(command.workAuthorizations(), true))
			.param("educationLevel", name(command.educationLevel()), Types.VARCHAR)
			.param("education", blankToNull(command.education()), Types.VARCHAR)
			.param("certifications", clean(command.certifications(), false))
			.param("salaryClt", command.desiredSalaryClt(), Types.NUMERIC)
			.param("salaryPj", command.desiredSalaryPj(), Types.NUMERIC)
			.param("weeklyGoal", (command.weeklyGoal() != null) ? command.weeklyGoal()
					: CandidateProfileView.DEFAULT_WEEKLY_GOAL)
			.param("now", Timestamp.from(this.clock.instant()))
			.update();
		this.events.publishEvent(new CandidateProfileChanged());
		return current();
	}

	/**
	 * Guarda o que foi lido do currículo (anos de experiência e inglês) como sugestão; os dados confirmados não
	 * mudam.
	 */
	public void suggest(@Nullable Integer yearsOfExperience, @Nullable EnglishLevel englishLevel, String resumeLabel) {
		if (yearsOfExperience == null && englishLevel == null) {
			return;
		}
		this.jdbc.sql("""
				insert into candidate_profile (id, suggested_years_of_experience, suggested_english_level, suggested_from)
				values (1, :years, :english, :from)
				on conflict (id) do update set
				    suggested_years_of_experience = excluded.suggested_years_of_experience,
				    suggested_english_level = excluded.suggested_english_level, suggested_from = excluded.suggested_from
				""")
			.param("years", yearsOfExperience, Types.INTEGER)
			.param("english", name(englishLevel), Types.VARCHAR)
			.param("from", resumeLabel.substring(0, Math.min(200, resumeLabel.length())))
			.update();
	}

	private static CandidateProfileView map(ResultSet rs) throws SQLException {
		return new CandidateProfileView(integer(rs, "years_of_experience"),
				JdbcRows.nullableEnum(rs, "english_level", EnglishLevel.class), rs.getString("city"),
				rs.getString("state"), integer(rs, "max_onsite_days"), list(rs.getArray("accepted_contracts")),
				list(rs.getArray("work_authorizations")),
				JdbcRows.nullableEnum(rs, "education_level", EducationLevel.class), rs.getString("education"),
				list(rs.getArray("certifications")), rs.getBigDecimal("desired_salary_clt"),
				rs.getBigDecimal("desired_salary_pj"), rs.getInt("weekly_goal"),
				integer(rs, "suggested_years_of_experience"),
				JdbcRows.nullableEnum(rs, "suggested_english_level", EnglishLevel.class), rs.getString("suggested_from"),
				JdbcRows.instant(rs, "updated_at"));
	}

	private static @Nullable Integer integer(ResultSet rs, String column) throws SQLException {
		int value = rs.getInt(column);
		return rs.wasNull() ? null : value;
	}

	private static List<String> list(@Nullable Array array) throws SQLException {
		return (array == null) ? List.of() : Arrays.asList((String[]) array.getArray());
	}

	private static String[] clean(@Nullable List<String> values, boolean upperCase) {
		if (values == null) {
			return new String[0];
		}
		return values.stream()
			.filter(value -> value != null && !value.isBlank())
			.map(String::strip)
			.map(value -> upperCase ? value.toUpperCase(Locale.ROOT) : value)
			.distinct()
			.toArray(String[]::new);
	}

	private static @Nullable String name(@Nullable Enum<?> value) {
		return (value != null) ? value.name() : null;
	}

	private static @Nullable String blankToNull(@Nullable String value) {
		return (value == null || value.isBlank()) ? null : value.strip();
	}

}
