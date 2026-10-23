package dev.laranja.jobhunter.jobs.internal;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import dev.laranja.jobhunter.companies.CompanyDirectory;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Corrige as vagas do LinkedIn lidas quando o parser pegava o link que envolve o card inteiro: o título vinha
 * com empresa, localidade e selos ("Candidatura simplificada"), a empresa recebia o título e a localidade vinha
 * como "Empresa · Localidade". Remove as empresas cadastradas com o título que ficaram sem vagas. Idempotente.
 */
@Component
class LinkedInCardLinkRepair implements ApplicationRunner {

	private static final Logger log = LoggerFactory.getLogger(LinkedInCardLinkRepair.class);

	private final JdbcClient jdbc;

	private final CompanyDirectory companies;

	LinkedInCardLinkRepair(JdbcClient jdbc, CompanyDirectory companies) {
		this.jdbc = jdbc;
		this.companies = companies;
	}

	@Override
	@Transactional
	public void run(ApplicationArguments args) {
		record Broken(long id, String company, String location, @Nullable Long companyId) {
		}
		List<Broken> broken = this.jdbc.sql("""
				select id, company, location, company_id from job
				where source = 'LINKEDIN_ALERTA' and position('·' in location) > 0
				  and starts_with(title, company || ' ' || location)
				""").query(Broken.class).list();
		Set<Long> previousCompanies = new LinkedHashSet<>();
		for (Broken job : broken) {
			String[] parts = job.location().split("·", 2);
			String company = parts[0].strip();
			String location = parts[1].strip();
			if (company.isEmpty()) {
				continue;
			}
			String title = job.company();
			String fingerprint = JobFingerprint.of(company, title, location);
			// Se a mesma vaga já entrou certa por outro alerta, mantém o fingerprint antigo para não violar o unique.
			this.jdbc.sql("""
					update job set title = :title, company = :company, location = :location, company_id = :companyId,
					    fingerprint = case when exists (select 1 from job other where other.fingerprint = :fingerprint)
					                       then fingerprint else :fingerprint end
					where id = :id
					""")
				.param("title", title)
				.param("company", company)
				.param("location", location.isEmpty() ? null : location)
				.param("companyId", this.companies.resolveOrRegister(company))
				.param("fingerprint", fingerprint)
				.param("id", job.id())
				.update();
			if (job.companyId() != null) {
				previousCompanies.add(job.companyId());
			}
		}
		previousCompanies.forEach(this.companies::removeIfUnused);
		if (!broken.isEmpty()) {
			log.info("{} vagas do LinkedIn corrigidas (título, empresa e localidade trocados).", broken.size());
		}
	}

}
