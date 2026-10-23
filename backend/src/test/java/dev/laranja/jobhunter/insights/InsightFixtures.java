package dev.laranja.jobhunter.insights;

import java.util.List;

import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * Dados semeados direto no banco (sem passar pelos eventos): dois currículos, vagas analisadas, candidaturas com
 * o histórico, feedbacks, e-mails classificados e uso da Claude API.
 */
final class InsightFixtures {

	private InsightFixtures() {
	}

	static void seed(JdbcClient jdbc) {
		jdbc.sql("""
				insert into resume_version (id, label, file_key, file_name, content_type, size_bytes, sha256, extracted_text,
				                            skills, is_default)
				values (1, 'Backend 2026', 'resumes/a.pdf', 'a.pdf', 'application/pdf', 10, 'sha-a',
				        'Desenvolvedor Java com 8 anos. Construí APIs REST com Spring Boot para pagamentos.',
				        '["Java", "Spring Boot"]', true),
				       (2, 'CV antigo', 'resumes/b.pdf', 'b.pdf', 'application/pdf', 10, 'sha-b', null, '["Java"]', false)
				""").update();
		jdbc.sql("""
				insert into company (id, name, normalized_name, watch_status)
				values (1, 'Banco Alfa', 'banco alfa', 'MONITORADA'), (2, 'Agência Beta', 'agencia beta', 'DESCOBERTA')
				""").update();
		for (int i = 1; i <= 12; i++) {
			long company = (i <= 10) ? 1 : 2;
			// 8 vagas pedem Kafka (lacuna); as vagas 7 a 10 são júnior e foram descartadas pela senioridade.
			String required = (i <= 8) ? "[\"Java\", \"Kafka\"]" : "[\"Java\", \"Spring Boot\"]";
			String gaps = (i <= 8) ? "[\"Kafka\"]" : "[]";
			String seniority = (i >= 7 && i <= 10) ? "JUNIOR" : "SENIOR";
			jdbc.sql("""
					insert into job (id, source, fingerprint, title, company, company_id, first_seen_at, last_seen_at,
					                 collected_at)
					values (:id, 'MANUAL', :fp, :title, :company, :companyId, now() - interval '3 days', now(), now())
					""")
				.param("id", i)
				.param("fp", "fp-" + i)
				.param("title", "Dev Java " + i)
				.param("company", (company == 1) ? "Banco Alfa" : "Agência Beta")
				.param("companyId", company)
				.update();
			jdbc.sql("""
					insert into job_analysis (job_id, status, seniority, required_skills, gaps, match_score, model_version,
					                          analyzed_at)
					values (:id, 'DETERMINISTICO', :seniority, cast(:required as jsonb), cast(:gaps as jsonb), :score,
					        'regras', now())
					""")
				.param("id", i)
				.param("seniority", seniority)
				.param("required", required)
				.param("gaps", gaps)
				.param("score", (i <= 8) ? 62 : 85)
				.update();
		}
		// Seis candidaturas: cinco com o currículo 1 (duas chegaram à entrevista, uma à oferta) e uma com o 2.
		for (int i = 1; i <= 6; i++) {
			long resume = (i == 6) ? 2 : 1;
			jdbc.sql("""
					insert into application (id, job_id, status, resume_version_id, applied_at)
					values (:id, :id, 'APLICADA', :resume, now())
					""").param("id", i).param("resume", resume).update();
			List<String> path = switch (i) {
				case 1 -> List.of("INTERESSE", "APLICADA", "TRIAGEM", "ENTREVISTA", "OFERTA");
				case 2 -> List.of("APLICADA", "ENTREVISTA", "RECUSADA");
				default -> List.of("APLICADA");
			};
			for (String status : path) {
				jdbc.sql("""
						insert into application_event (application_id, to_status, origin) values (:id, :status, 'MANUAL')
						""").param("id", i).param("status", status).update();
			}
		}
		// Descartes: quatro júnior pela senioridade, mais um sem análise de senioridade; duas vagas da agência.
		for (int i = 7; i <= 12; i++) {
			String reasons = (i <= 10) ? "{SENIORIDADE}" : "{EMPRESA,SENIORIDADE}";
			jdbc.sql("""
					insert into job_feedback (job_id, rating, reasons) values (:id, 'NAO_GOSTEI', cast(:reasons as text[]))
					""").param("id", i).param("reasons", reasons).update();
		}
		for (int i = 1; i <= 5; i++) {
			jdbc.sql("""
					insert into email_message (gmail_id, sender, received_at, kind, ai_classification, final_classification,
					                           classified_by)
					values (:gmailId, 'rh@alfa.example', now(), 'OUTRO', 'CONVITE_ENTREVISTA', :final, 'USUARIO')
					""")
				.param("gmailId", "g-" + i)
				.param("final", (i == 5) ? "IRRELEVANTE" : "CONVITE_ENTREVISTA")
				.update();
		}
		jdbc.sql("""
				insert into llm_usage (task, model, input_tokens, output_tokens) values ('match-score', 'claude-haiku-5-5', 1000000, 0)
				""").update();
		for (String table : List.of("resume_version", "company", "job", "application")) {
			jdbc.sql("select setval(pg_get_serial_sequence('" + table + "', 'id'), 100)").query(Long.class).single();
		}
	}

}
