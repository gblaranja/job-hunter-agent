package dev.laranja.jobhunter.recognition.internal;

import java.util.List;

import dev.laranja.jobhunter.companies.CompanyNameNormalizer;
import org.jspecify.annotations.Nullable;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * Casa nomes com as empresas cadastradas (nome normalizado, aliases e similaridade por trigramas) e encontra os
 * homônimos de uma empresa.
 */
@Component
public class CompanyMatcher {

	/** Similaridade mínima (pg_trgm) para sugerir uma empresa na importação em lote. */
	static final double MIN_SIMILARITY = 0.45;

	private final JdbcClient jdbc;

	CompanyMatcher(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	public Match match(String name) {
		String normalized = CompanyNameNormalizer.normalize(name);
		if (normalized.isEmpty()) {
			return new Match(null, null, MatchKind.NENHUM, 0);
		}
		record Row(long id, String name, double similarity) {
		}
		Row exact = this.jdbc.sql("""
				select c.id, c.name, 1.0::float8 as similarity from company c
				where c.normalized_name = :normalized
				   or exists (select 1 from company_alias a where a.company_id = c.id and a.alias = :normalized)
				limit 1
				""").param("normalized", normalized).query(Row.class).optional().orElse(null);
		if (exact != null) {
			return new Match(exact.id(), exact.name(), MatchKind.EXATO, 1);
		}
		return this.jdbc.sql("""
				select id, name, similarity(normalized_name, :normalized)::float8 as similarity from company
				where similarity(normalized_name, :normalized) >= :min
				order by similarity desc, id
				limit 1
				""")
			.param("normalized", normalized)
			.param("min", MIN_SIMILARITY)
			.query(Row.class)
			.optional()
			.map(row -> new Match(row.id(), row.name(), MatchKind.SIMILAR, row.similarity()))
			.orElse(new Match(null, null, MatchKind.NENHUM, 0));
	}

	/** Outras empresas com nome parecido ou que começam pelo mesmo nome ("Stone" e "Stone Pagamentos"). */
	public List<String> homonyms(long companyId, String normalizedName) {
		return this.jdbc.sql("""
				select name from company
				where id <> :id
				  and (similarity(normalized_name, :normalized) >= 0.5
				       or normalized_name like :normalized || ' %'
				       or :normalized like normalized_name || ' %')
				order by similarity(normalized_name, :normalized) desc
				limit 5
				""").param("id", companyId).param("normalized", normalizedName).query(String.class).list();
	}

	public List<String> aliases(long companyId) {
		return this.jdbc.sql("select alias from company_alias where company_id = :id")
			.param("id", companyId)
			.query(String.class)
			.list();
	}

	public enum MatchKind {

		EXATO, SIMILAR, NENHUM

	}

	public record Match(@Nullable Long companyId, @Nullable String companyName, MatchKind kind, double similarity) {
	}

}
