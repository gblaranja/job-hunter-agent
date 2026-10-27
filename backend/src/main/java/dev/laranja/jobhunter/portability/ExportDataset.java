package dev.laranja.jobhunter.portability;

/**
 * Conjuntos de dados que dá para exportar (B-41, LGPD): os seus dados em JSON ou CSV, para guardar ou levar para outra
 * ferramenta. Cada um é lido direto do banco, por SQL, sem passar pelos módulos donos.
 */
public enum ExportDataset {

	/** Vagas coletadas ou cadastradas, com a análise. */
	VAGAS("""
			select j.id, j.source, j.title, j.company, j.location, j.work_model, j.scope, j.job_family,
			       j.international_eligibility, j.salary_range, j.url, j.first_seen_at, j.closed_at, an.match_score,
			       an.seniority, an.required_skills::text as required_skills, an.summary, d.reason as dismissal_reason,
			       f.rating as feedback
			from job j
			left join job_analysis an on an.job_id = j.id
			left join job_dismissal d on d.job_id = j.id
			left join job_feedback f on f.job_id = j.id
			order by j.id
			"""),

	/** Candidaturas, com a vaga e o currículo usado. */
	CANDIDATURAS("""
			select a.id, a.job_id, j.title, j.company, a.status, a.channel, a.applied_at, r.label as resume,
			       a.next_action_at, a.notes, a.created_at, a.updated_at
			from application a
			join job j on j.id = a.job_id
			left join resume_version r on r.id = a.resume_version_id
			order by a.id
			"""),

	/** Linha do tempo das candidaturas. */
	EVENTOS("""
			select e.id, e.application_id, j.title, j.company, e.from_status, e.to_status, e.response_type,
			       e.response_channel, e.origin, e.note, e.created_at
			from application_event e
			join application a on a.id = e.application_id
			join job j on j.id = a.job_id
			order by e.application_id, e.created_at, e.id
			"""),

	/** Contatos e quantas conversas você registrou com cada um. */
	CONTATOS("""
			select c.id, c.name, c.role, c.company_name, c.position, c.linkedin_url, c.email, c.source,
			       c.connected_on, c.notes, c.created_at,
			       (select count(*) from contact_interaction i where i.contact_id = c.id) as interactions
			from contact c
			order by c.id
			"""),

	/** Conversas registradas com os contatos. */
	CONVERSAS("""
			select i.id, i.contact_id, c.name as contact, i.application_id, i.channel, i.direction, i.kind, i.summary,
			       i.occurred_at
			from contact_interaction i
			join contact c on c.id = i.contact_id
			order by i.contact_id, i.occurred_at, i.id
			""");

	private final String sql;

	ExportDataset(String sql) {
		this.sql = sql;
	}

	String sql() {
		return this.sql;
	}

}
