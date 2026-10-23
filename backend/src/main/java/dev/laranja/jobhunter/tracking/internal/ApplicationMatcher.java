package dev.laranja.jobhunter.tracking.internal;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import dev.laranja.jobhunter.companies.CompanyNameNormalizer;
import org.jspecify.annotations.Nullable;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * Encontra a candidatura aberta de um retorno: pela empresa citada no e-mail (nome normalizado ou alias) ou pelo
 * domínio do remetente ("rh@bancoexemplo.com.br" casa com "Banco Exemplo").
 */
@Component
class ApplicationMatcher {

	/** Domínios de e-mail pessoal e de plataformas: não identificam a empresa. */
	private static final Set<String> GENERIC_DOMAINS = Set.of("gmail", "outlook", "hotmail", "yahoo", "icloud", "live",
			"gupy", "linkedin", "greenhouse", "lever", "workable", "kenoby", "solides", "inhire", "indeed", "glassdoor",
			"ashbyhq", "smartrecruiters", "myworkday", "workday", "email", "mail");

	private static final Set<String> SECOND_LEVEL = Set.of("com", "net", "org", "gov", "edu", "co", "ind");

	private static final String OPEN = "a.status not in ('OFERTA', 'RECUSADA', 'DESISTI', 'ENCERRADA')";

	private final JdbcClient jdbc;

	ApplicationMatcher(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	List<Long> match(@Nullable String company, String sender) {
		Set<Long> found = new LinkedHashSet<>();
		if (company != null && !company.isBlank()) {
			found.addAll(this.jdbc.sql("""
					select distinct a.id from application a
					join job j on j.id = a.job_id
					join company c on c.id = j.company_id
					left join company_alias al on al.company_id = c.id
					where %s and (c.normalized_name = :name or al.alias = :name)
					""".formatted(OPEN))
				.param("name", CompanyNameNormalizer.normalize(company))
				.query(Long.class)
				.list());
		}
		String domain = domainCore(sender);
		if (found.isEmpty() && domain != null) {
			found.addAll(this.jdbc.sql("""
					select distinct a.id from application a
					join job j on j.id = a.job_id
					join company c on c.id = j.company_id
					where %s and replace(c.normalized_name, ' ', '') = :domain
					""".formatted(OPEN))
				.param("domain", domain)
				.query(Long.class)
				.list());
		}
		return List.copyOf(found);
	}

	/** "rh.bancoexemplo.com.br" vira "bancoexemplo"; domínios genéricos não contam. */
	static @Nullable String domainCore(String sender) {
		int at = sender.lastIndexOf('@');
		if (at < 0) {
			return null;
		}
		String[] labels = sender.substring(at + 1).toLowerCase(Locale.ROOT).split("\\.");
		int end = labels.length;
		if (end > 2 && labels[end - 1].length() == 2 && SECOND_LEVEL.contains(labels[end - 2])) {
			end--; // código do país depois de um sufixo genérico: .com.br, .co.uk
		}
		if (end > 1) {
			end--; // sufixo: .com, .io, .tech
		}
		String core = labels[end - 1];
		return (core.isEmpty() || GENERIC_DOMAINS.contains(core)) ? null : core;
	}

}
