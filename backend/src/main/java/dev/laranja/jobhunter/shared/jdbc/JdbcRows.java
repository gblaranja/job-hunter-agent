package dev.laranja.jobhunter.shared.jdbc;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;

/** Leitura de colunas que o mapeamento automático do JdbcClient não converte bem. */
public final class JdbcRows {

	private JdbcRows() {
	}

	public static Instant instant(ResultSet rs, String column) throws SQLException {
		OffsetDateTime value = rs.getObject(column, OffsetDateTime.class);
		return (value != null) ? value.toInstant() : null;
	}

	public static Long nullableLong(ResultSet rs, String column) throws SQLException {
		long value = rs.getLong(column);
		return rs.wasNull() ? null : value;
	}

	public static <E extends Enum<E>> E nullableEnum(ResultSet rs, String column, Class<E> type) throws SQLException {
		String value = rs.getString(column);
		return (value != null) ? Enum.valueOf(type, value) : null;
	}

	/** Padrão ILIKE de "contém", com os curingas do usuário escapados; {@code null} quando a busca está vazia. */
	public static String containsPattern(String query) {
		if (query == null || query.isBlank()) {
			return null;
		}
		String escaped = query.strip().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
		return "%" + escaped + "%";
	}

}
