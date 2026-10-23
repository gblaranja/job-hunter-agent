package dev.laranja.jobhunter.companies;

import java.time.Clock;
import java.util.Objects;

import dev.laranja.jobhunter.companies.internal.Company;
import dev.laranja.jobhunter.companies.internal.CompanyRepository;
import dev.laranja.jobhunter.shared.BusinessRuleException;
import dev.laranja.jobhunter.shared.NotFoundException;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Cadastro de empresas: toda empresa que aparece numa vaga passa por aqui. */
@Service
@Transactional
public class CompanyDirectory {

	private final CompanyRepository companies;

	private final JdbcClient jdbc;

	private final ApplicationEventPublisher events;

	private final Clock clock;

	CompanyDirectory(CompanyRepository companies, JdbcClient jdbc, ApplicationEventPublisher events, Clock clock) {
		this.companies = companies;
		this.jdbc = jdbc;
		this.events = events;
		this.clock = clock;
	}

	/** Encontra a empresa pelo nome normalizado ou por um alias; sem correspondência, cadastra como descoberta. */
	public long resolveOrRegister(String name) {
		String normalized = CompanyNameNormalizer.normalize(name);
		if (normalized.isEmpty()) {
			throw new BusinessRuleException("O nome da empresa está vazio.");
		}
		return this.companies.findByNormalizedName(normalized)
			.or(() -> this.companies.findByAlias(normalized))
			.map(Company::getId)
			.orElseGet(() -> register(name, normalized));
	}

	private long register(String name, String normalized) {
		Company company = this.companies.save(Company.discovered(name, normalized, this.clock.instant()));
		this.events.publishEvent(new CompanyDiscovered(company.getId(), company.getName()));
		return company.getId();
	}

	public void update(long id, UpdateCompanyCommand command) {
		require(id).update(command.watchStatus(), command.website(), command.atsType(), command.atsSlug(),
				command.notes());
	}

	/**
	 * Junta duas empresas que são a mesma ("Itaú" e "Itaú Unibanco"): as vagas e os aliases passam para o
	 * destino, e o nome da origem vira um alias dele.
	 */
	public void merge(long sourceId, long targetId) {
		if (sourceId == targetId) {
			throw new BusinessRuleException("Escolha outra empresa para mesclar.");
		}
		Company source = require(sourceId);
		Company target = require(targetId);
		this.jdbc.sql("update job set company_id = :target where company_id = :source")
			.param("target", target.getId())
			.param("source", source.getId())
			.update();
		this.jdbc.sql("update company_alias set company_id = :target where company_id = :source")
			.param("target", target.getId())
			.param("source", source.getId())
			.update();
		this.companies.delete(source);
		this.companies.flush();
		if (!Objects.equals(source.getNormalizedName(), target.getNormalizedName())) {
			this.jdbc.sql("insert into company_alias (company_id, alias) values (:target, :alias) on conflict (alias) do nothing")
				.param("target", target.getId())
				.param("alias", source.getNormalizedName())
				.update();
		}
	}

	/** Apaga uma empresa descoberta que ficou sem vagas, como as cadastradas por engano com o título da vaga. */
	public void removeIfUnused(long id) {
		this.jdbc.sql("""
				delete from company
				where id = :id and watch_status = 'DESCOBERTA'
				  and not exists (select 1 from job where company_id = :id)
				""").param("id", id).update();
	}

	private Company require(long id) {
		return this.companies.findById(id).orElseThrow(() -> NotFoundException.of("Empresa", id));
	}

}
