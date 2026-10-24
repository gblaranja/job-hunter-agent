package dev.laranja.jobhunter.companies;

import java.sql.Timestamp;
import java.sql.Types;
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

	/**
	 * Cadastra ou atualiza uma empresa vinda de uma base pública (Banco Central, Receita), casando pela raiz do CNPJ
	 * e depois pelo nome. Entra como descoberta e não publica {@link CompanyDiscovered}: as importações trazem
	 * milhares de empresas e não devem disparar buscas pagas.
	 */
	public SeededCompany upsertSeeded(SeedCompanyCommand command) {
		String normalized = CompanyNameNormalizer.normalize(command.name());
		if (normalized.isEmpty()) {
			throw new BusinessRuleException("O nome da empresa está vazio.");
		}
		Long existing = this.jdbc.sql("select id from company where cnpj = :cnpj")
			.param("cnpj", command.cnpj())
			.query(Long.class)
			.optional()
			.or(() -> this.companies.findByNormalizedName(normalized)
				.or(() -> this.companies.findByAlias(normalized))
				.map(Company::getId))
			.orElse(null);
		if (existing != null) {
			this.jdbc.sql("""
					update company set
					    cnpj = coalesce(cnpj, case when not exists (select 1 from company o where o.cnpj = :cnpj) then :cnpj end),
					    authorization_type = :authorization, website = coalesce(website, :website)
					where id = :id
					""")
				.param("id", existing)
				.param("cnpj", command.cnpj())
				.param("authorization", command.authorizationType())
				.param("website", command.website(), Types.VARCHAR)
				.update();
			return new SeededCompany(existing, false);
		}
		long id = this.jdbc.sql("""
				insert into company (name, normalized_name, website, hq_location, kind, watch_status, cnpj,
				                     authorization_type, first_seen_at)
				values (:name, :normalized, :website, :location, :kind, 'DESCOBERTA', :cnpj, :authorization, :now)
				returning id
				""")
			.param("name", command.name().strip())
			.param("normalized", normalized)
			.param("website", command.website(), Types.VARCHAR)
			.param("location", command.location(), Types.VARCHAR)
			.param("kind", command.kind().name())
			.param("cnpj", command.cnpj())
			.param("authorization", command.authorizationType())
			.param("now", Timestamp.from(this.clock.instant()))
			.query(Long.class)
			.single();
		return new SeededCompany(id, true);
	}

	/** Uma empresa descoberta passa a ser monitorada (fit alto no nicho ou vaga de tecnologia publicada). */
	public boolean promoteToMonitored(long id) {
		return this.jdbc.sql("update company set watch_status = 'MONITORADA' where id = :id and watch_status = 'DESCOBERTA'")
			.param("id", id)
			.update() > 0;
	}

	/** Muda só o acompanhamento, sem tocar nos demais dados (botão "Marcar como alvo" da tela Nichos). */
	public void changeWatchStatus(long id, WatchStatus watchStatus) {
		Company company = require(id);
		company.changeWatchStatus(watchStatus);
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
		// O histórico dos selos vale para a empresa mesclada; nicho e fit são recalculados para o destino.
		this.jdbc.sql("update company_recognition set company_id = :target where company_id = :source")
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
