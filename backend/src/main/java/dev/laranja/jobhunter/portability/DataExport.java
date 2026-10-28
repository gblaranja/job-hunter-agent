package dev.laranja.jobhunter.portability;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import tools.jackson.databind.json.JsonMapper;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Exporta um conjunto de dados em JSON (lista de objetos) ou CSV (com cabeçalho, separado por vírgula, UTF-8). */
@Service
@Transactional(readOnly = true)
public class DataExport {

	private final JdbcClient jdbc;

	private final JsonMapper json;

	DataExport(JdbcClient jdbc, JsonMapper json) {
		this.jdbc = jdbc;
		this.json = json;
	}

	public List<Map<String, Object>> rows(ExportDataset dataset) {
		return this.jdbc.sql(dataset.sql()).query().listOfRows().stream().map(DataExport::plain).toList();
	}

	public String json(ExportDataset dataset) {
		return this.json.writeValueAsString(rows(dataset));
	}

	public String csv(ExportDataset dataset) {
		List<Map<String, Object>> rows = rows(dataset);
		List<String> columns = rows.isEmpty() ? columnsOf(dataset) : List.copyOf(rows.get(0).keySet());
		StringBuilder csv = new StringBuilder(String.join(",", columns)).append("\r\n");
		for (Map<String, Object> row : rows) {
			csv.append(columns.stream().map(column -> cell(row.get(column))).collect(Collectors.joining(","))).append("\r\n");
		}
		return csv.toString();
	}

	private List<String> columnsOf(ExportDataset dataset) {
		return this.jdbc.sql("select * from (" + dataset.sql() + ") as t limit 0")
			.query(rs -> {
				var meta = rs.getMetaData();
				List<String> names = new java.util.ArrayList<>();
				for (int i = 1; i <= meta.getColumnCount(); i++) {
					names.add(meta.getColumnLabel(i));
				}
				return names;
			});
	}

	private static Map<String, Object> plain(Map<String, Object> row) {
		Map<String, Object> copy = new LinkedHashMap<>();
		row.forEach((key, value) -> copy.put(key, (value instanceof Timestamp ts) ? ts.toInstant().toString()
				: (value instanceof java.sql.Date date) ? date.toLocalDate().toString() : value));
		return copy;
	}

	// Aspas em tudo que tem vírgula, aspas ou quebra de linha; fórmulas de planilha ficam neutralizadas com um apóstrofo.
	private static String cell(Object value) {
		if (value == null) {
			return "";
		}
		String text = (value instanceof Instant instant) ? instant.toString() : value.toString();
		if (!text.isEmpty() && "=+-@".indexOf(text.charAt(0)) >= 0 && !(value instanceof Number)) {
			text = "'" + text;
		}
		if (text.contains(",") || text.contains("\"") || text.contains("\n") || text.contains("\r")) {
			return "\"" + text.replace("\"", "\"\"") + "\"";
		}
		return text;
	}

}
