package dev.laranja.jobhunter.recognition;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;

import dev.laranja.jobhunter.shared.NotFoundException;
import dev.laranja.jobhunter.shared.jdbc.JdbcRows;

import org.springframework.jdbc.core.simple.JdbcClient;

/** Leituras de {@code company_recognition} com o nome da empresa. */
final class RecognitionQueries {

	static final String SELECT = """
			select r.*, c.name as company_name, c.normalized_name as company_normalized_name from company_recognition r join company c on c.id = r.company_id
			""";

	private RecognitionQueries() {
	}

	static RecognitionView get(JdbcClient jdbc, long id, LocalDate today) {
		return jdbc.sql(SELECT + " where r.id = :id")
			.param("id", id)
			.query((rs, row) -> map(rs, today, List.of()))
			.optional()
			.orElseThrow(() -> NotFoundException.of("Reconhecimento", id));
	}

	static RecognitionView map(ResultSet rs, LocalDate today, List<String> homonyms) throws SQLException {
		RecognitionLevel level = RecognitionLevel.valueOf(rs.getString("level"));
		LocalDate validUntil = (rs.getDate("valid_until") != null) ? rs.getDate("valid_until").toLocalDate() : null;
		if (level.visible() && validUntil != null && validUntil.isBefore(today)) {
			level = RecognitionLevel.EXPIRADA;
		}
		return new RecognitionView(rs.getLong("id"), rs.getLong("company_id"), rs.getString("company_name"),
				rs.getString("program"), level, rs.getString("ranking_name"), (Integer) rs.getObject("ranking_position"),
				(Integer) rs.getObject("year"), validUntil, rs.getString("evidence_url"), rs.getString("evidence_excerpt"),
				rs.getString("rationale"), rs.getString("matched_name"), rs.getBigDecimal("confidence").doubleValue(),
				rs.getString("verified_by"), rs.getString("review_status"), rs.getString("source"),
				JdbcRows.instant(rs, "checked_at"), homonyms);
	}

}
