package dev.laranja.jobhunter.network;

import java.sql.Types;
import java.time.Clock;
import java.time.Instant;
import java.util.List;

import dev.laranja.jobhunter.companies.CompanyDirectory;
import dev.laranja.jobhunter.network.internal.Contact;
import dev.laranja.jobhunter.network.internal.ContactInteraction;
import dev.laranja.jobhunter.network.internal.ContactInteractionRepository;
import dev.laranja.jobhunter.network.internal.ContactRepository;
import dev.laranja.jobhunter.network.internal.ProfileUrls;
import dev.laranja.jobhunter.shared.ConflictException;
import dev.laranja.jobhunter.shared.NotFoundException;
import dev.laranja.jobhunter.shared.jdbc.JdbcRows;
import dev.laranja.jobhunter.shared.web.PageResponse;
import org.jspecify.annotations.Nullable;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Contatos e o histórico de conversas com eles. Guarda só o necessário (D-05) e apagar um contato apaga o histórico
 * dele. A empresa do contato é casada com o cadastro pelo nome, sem criar empresa.
 */
@Service
@Transactional
public class ContactBook {

	static final String VIEW_SELECT = """
			select c.id, c.name, c.role, c.company_id, coalesce(c.company_name, co.name) as company_name, c.position,
			       c.linkedin_url, c.email, c.notes, c.source, c.connected_on, c.created_at,
			       (select max(i.occurred_at) from contact_interaction i where i.contact_id = c.id) as last_interaction_at
			from contact c
			left join company co on co.id = c.company_id
			""";

	/** Contatos listados por empresa no detalhe da vaga e na candidatura. */
	private static final int COMPANY_LIMIT = 100;

	private final ContactRepository contacts;

	private final ContactInteractionRepository interactions;

	private final CompanyDirectory companies;

	private final JdbcClient jdbc;

	private final Clock clock;

	ContactBook(ContactRepository contacts, ContactInteractionRepository interactions, CompanyDirectory companies,
			JdbcClient jdbc, Clock clock) {
		this.contacts = contacts;
		this.interactions = interactions;
		this.companies = companies;
		this.jdbc = jdbc;
		this.clock = clock;
	}

	/**
	 * Contatos, os com conversa mais recente primeiro.
	 * @param query parte do nome, da empresa ou do cargo
	 */
	@Transactional(readOnly = true)
	public PageResponse<ContactView> list(@Nullable String query, @Nullable Long companyId, @Nullable ContactRole role,
			int page, int size) {
		String filter = """
				where (cast(:pattern as text) is null or c.name ilike :pattern or c.company_name ilike :pattern
				       or c.position ilike :pattern or co.name ilike :pattern)
				  and (cast(:companyId as bigint) is null or c.company_id = :companyId)
				  and (cast(:role as text) is null or c.role = :role)
				""";
		String pattern = JdbcRows.containsPattern(query);
		List<ContactView> items = this.jdbc.sql(VIEW_SELECT + filter + """
				order by last_interaction_at desc nulls last, (c.role = 'CONEXAO'), c.name, c.id
				limit :size offset :offset
				""")
			.param("pattern", pattern, Types.VARCHAR)
			.param("companyId", companyId, Types.BIGINT)
			.param("role", (role != null) ? role.name() : null, Types.VARCHAR)
			.param("size", size)
			.param("offset", page * size)
			.query((rs, row) -> ContactRows.view(rs))
			.list();
		long total = this.jdbc.sql("select count(*) from contact c left join company co on co.id = c.company_id " + filter)
			.param("pattern", pattern, Types.VARCHAR)
			.param("companyId", companyId, Types.BIGINT)
			.param("role", (role != null) ? role.name() : null, Types.VARCHAR)
			.query(Long.class)
			.single();
		return new PageResponse<>(items, page, size, total);
	}

	@Transactional(readOnly = true)
	public ContactView get(long id) {
		return this.jdbc.sql(VIEW_SELECT + " where c.id = :id")
			.param("id", id)
			.query((rs, row) -> ContactRows.view(rs))
			.optional()
			.orElseThrow(() -> NotFoundException.of("Contato", id));
	}

	public ContactView create(ContactCommands.Save command) {
		String url = ProfileUrls.normalize(command.linkedinUrl());
		rejectDuplicateProfile(url, null);
		Contact contact = this.contacts.save(Contact.create(command.name(), command.role(), ContactSource.MANUAL,
				companyIdOf(command.company()), command.company(), command.position(), url, command.email(),
				command.notes(), null, this.clock.instant()));
		this.contacts.flush();
		return get(contact.getId());
	}

	public ContactView update(long id, ContactCommands.Save command) {
		Contact contact = require(id);
		String url = ProfileUrls.normalize(command.linkedinUrl());
		rejectDuplicateProfile(url, id);
		contact.edit(command.name(), command.role(), companyIdOf(command.company()), command.company(),
				command.position(), url, command.email(), command.notes(), this.clock.instant());
		this.contacts.flush();
		return get(id);
	}

	/** Apaga o contato e todas as conversas registradas com ele (LGPD). */
	public void delete(long id) {
		this.contacts.delete(require(id));
	}

	/** Registra uma conversa com o contato, ligada ou não a uma candidatura. */
	public long recordInteraction(long contactId, ContactCommands.RecordInteraction command) {
		require(contactId);
		if (command.applicationId() != null && !applicationExists(command.applicationId())) {
			throw NotFoundException.of("Candidatura", command.applicationId());
		}
		Instant now = this.clock.instant();
		return this.interactions
			.save(ContactInteraction.record(contactId, command.applicationId(), command.channel(), command.direction(),
					command.kind(), command.summary(), null, (command.occurredAt() != null) ? command.occurredAt() : now,
					now))
			.getId();
	}

	@Transactional(readOnly = true)
	public List<ContactInteractionView> interactions(long contactId) {
		require(contactId);
		return this.jdbc.sql(ContactRows.INTERACTION_SELECT + " where i.contact_id = :id order by i.occurred_at desc, i.id desc")
			.param("id", contactId)
			.query((rs, row) -> ContactRows.interaction(rs))
			.list();
	}

	/**
	 * Quem você conhece na empresa da vaga: primeiro os contatos que você cadastrou (recrutador, gestor, quem pode
	 * indicar) e depois as conexões do LinkedIn.
	 */
	@Transactional(readOnly = true)
	public List<ContactView> forJob(long jobId) {
		return this.jdbc.sql(VIEW_SELECT + """
				where c.company_id = (select j.company_id from job j where j.id = :jobId)
				order by (c.role = 'CONEXAO'), last_interaction_at desc nulls last, c.name
				limit :limit
				""")
			.param("jobId", jobId)
			.param("limit", COMPANY_LIMIT)
			.query((rs, row) -> ContactRows.view(rs))
			.list();
	}

	/**
	 * Contatos da candidatura (os que já conversaram com você sobre ela e os da empresa da vaga) e as conversas
	 * registradas nela, para a linha do tempo.
	 */
	@Transactional(readOnly = true)
	public ApplicationContacts forApplication(long applicationId) {
		if (!applicationExists(applicationId)) {
			throw NotFoundException.of("Candidatura", applicationId);
		}
		List<ContactView> contacts = this.jdbc.sql(VIEW_SELECT + """
				where c.id in (select i.contact_id from contact_interaction i where i.application_id = :id)
				   or c.company_id = (select j.company_id from application a join job j on j.id = a.job_id where a.id = :id)
				order by exists (select 1 from contact_interaction i where i.contact_id = c.id and i.application_id = :id) desc,
				         (c.role = 'CONEXAO'), last_interaction_at desc nulls last, c.name
				limit :limit
				""")
			.param("id", applicationId)
			.param("limit", COMPANY_LIMIT)
			.query((rs, row) -> ContactRows.view(rs))
			.list();
		List<ContactInteractionView> interactions = this.jdbc
			.sql(ContactRows.INTERACTION_SELECT + " where i.application_id = :id order by i.occurred_at, i.id")
			.param("id", applicationId)
			.query((rs, row) -> ContactRows.interaction(rs))
			.list();
		return new ApplicationContacts(contacts, interactions);
	}

	/** @param interactions conversas registradas nesta candidatura, da mais antiga à mais recente */
	public record ApplicationContacts(List<ContactView> contacts, List<ContactInteractionView> interactions) {
	}

	private @Nullable Long companyIdOf(@Nullable String company) {
		return (company == null || company.isBlank()) ? null : this.companies.findId(company).orElse(null);
	}

	private void rejectDuplicateProfile(@Nullable String url, @Nullable Long self) {
		if (url == null) {
			return;
		}
		this.contacts.findByLinkedinUrl(url)
			.filter(existing -> !existing.getId().equals(self))
			.ifPresent(existing -> {
				throw new ConflictException("Já existe um contato com esse perfil do LinkedIn.", existing.getId());
			});
	}

	private boolean applicationExists(long applicationId) {
		return this.jdbc.sql("select count(*) from application where id = :id")
			.param("id", applicationId)
			.query(Long.class)
			.single() > 0;
	}

	private Contact require(long id) {
		return this.contacts.findById(id).orElseThrow(() -> NotFoundException.of("Contato", id));
	}

}
