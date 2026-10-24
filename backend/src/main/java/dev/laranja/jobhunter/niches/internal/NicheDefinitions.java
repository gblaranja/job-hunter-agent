package dev.laranja.jobhunter.niches.internal;

import java.sql.Array;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import dev.laranja.jobhunter.niches.internal.FitCalculator.Weights;
import org.jspecify.annotations.Nullable;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/** Nichos ativos com os subsegmentos, como a classificação e o fit usam. */
@Component
public class NicheDefinitions {

	private static final TypeReference<Map<String, Double>> WEIGHTS = new TypeReference<>() {
	};

	private final JdbcClient jdbc;

	private final JsonMapper json;

	NicheDefinitions(JdbcClient jdbc, JsonMapper json) {
		this.jdbc = jdbc;
		this.json = json;
	}

	public List<NicheDef> active() {
		List<SegmentRow> segments = this.jdbc.sql("""
				select s.id, s.niche_id, s.name, s.description, s.base_fit, s.keywords, s.authorization_types
				from niche_segment s join niche n on n.id = s.niche_id where n.active order by s.id
				""")
			.query((rs, row) -> new SegmentRow(rs.getLong("niche_id"), new SegmentDef(rs.getLong("id"), rs.getString("name"),
					rs.getString("description"), rs.getInt("base_fit"), strings(rs.getArray("keywords")),
					strings(rs.getArray("authorization_types")))))
			.list();
		return this.jdbc.sql("select id, name, fit_weights::text as weights, company_kinds, domain_terms from niche where active order by id")
			.query((rs, row) -> {
				long id = rs.getLong("id");
				return new NicheDef(id, rs.getString("name"), strings(rs.getArray("company_kinds")),
						strings(rs.getArray("domain_terms")), weights(rs.getString("weights")),
						segments.stream().filter(segment -> segment.nicheId() == id).map(SegmentRow::segment).toList());
			})
			.list();
	}

	public Weights weights(String text) {
		Map<String, Double> values = this.json.readValue(text, WEIGHTS);
		return new Weights(values.getOrDefault("segment", 0.35), values.getOrDefault("stack", 0.30),
				values.getOrDefault("domain", 0.20), values.getOrDefault("signals", 0.15));
	}

	static List<String> strings(@Nullable Array array) throws SQLException {
		return (array == null) ? List.of() : Arrays.asList((String[]) array.getArray());
	}

	private record SegmentRow(long nicheId, SegmentDef segment) {
	}

	/** @param companyKinds tipos de empresa aceitos no nicho; vazio aceita qualquer um */
	public record NicheDef(long id, String name, List<String> companyKinds, List<String> domainTerms, Weights weights,
			List<SegmentDef> segments) {

		public boolean accepts(String kind) {
			return this.companyKinds.isEmpty() || this.companyKinds.contains(kind);
		}

	}

	public record SegmentDef(long id, String name, @Nullable String description, int baseFit, List<String> keywords,
			List<String> authorizationTypes) {
	}

}
