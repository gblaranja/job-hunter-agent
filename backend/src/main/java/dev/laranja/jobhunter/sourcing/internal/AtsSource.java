package dev.laranja.jobhunter.sourcing.internal;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import dev.laranja.jobhunter.companies.CompanyMonitoring;
import dev.laranja.jobhunter.companies.TrackedCompany;
import dev.laranja.jobhunter.jobs.JobCatalog;
import dev.laranja.jobhunter.jobs.JobOrigin;
import dev.laranja.jobhunter.jobs.RawJob;
import dev.laranja.jobhunter.sourcing.JobSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Base dos ATS com job board público: consulta cada empresa acompanhada e fecha as vagas que sumiram da listagem.
 * Uma empresa com erro na consulta não tem nenhuma vaga fechada.
 */
abstract class AtsSource implements JobSource {

	private static final Logger log = LoggerFactory.getLogger(AtsSource.class);

	private final CompanyMonitoring companies;

	private final JobCatalog catalog;

	AtsSource(CompanyMonitoring companies, JobCatalog catalog) {
		this.companies = companies;
		this.catalog = catalog;
	}

	abstract JobOrigin origin();

	abstract List<RawJob> list(TrackedCompany company);

	@Override
	public String id() {
		return origin().name();
	}

	@Override
	public boolean applyExclusions() {
		return false;
	}

	@Override
	public boolean available() {
		return !this.companies.tracked(origin().name()).isEmpty();
	}

	@Override
	public void collect(Consumer<RawJob> sink) {
		for (TrackedCompany company : this.companies.tracked(origin().name())) {
			List<RawJob> jobs;
			try {
				jobs = list(company);
			}
			catch (RuntimeException ex) {
				log.warn("{}: falha ao consultar {} ({}): {}", origin(), company.name(), company.atsSlug(), ex.getMessage());
				continue;
			}
			List<String> listed = new ArrayList<>();
			for (RawJob job : jobs) {
				sink.accept(job);
				listed.add(job.externalId());
			}
			int closed = this.catalog.closeMissing(origin(), company.id(), listed);
			log.info("{}: {} vagas abertas em {}; {} fechadas.", origin(), jobs.size(), company.name(), closed);
		}
	}

}
