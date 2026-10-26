package dev.laranja.jobhunter.jobs.internal;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import dev.laranja.jobhunter.jobs.Legitimacy;
import dev.laranja.jobhunter.shared.ScamSignals;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.json.JsonMapper;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Avalia se a vaga é real: sinais de vaga fantasma (antiga, republicada, link falhando, fora do ATS da empresa,
 * agência sem cliente, descrição genérica) e de golpe ({@link ScamSignals}). Determinístico, sem IA. Roda na
 * ingestão, quando a descrição chega e todo dia às 6h45 para as vagas abertas (a idade muda com o tempo).
 */
@Component
public class LegitimacyAssessor {

	private static final Logger log = LoggerFactory.getLogger(LegitimacyAssessor.class);

	/** Abaixo disso (em caracteres), a descrição conta como genérica. */
	static final int GENERIC_DESCRIPTION = 300;

	/** A ausência no ATS só vale para vagas vistas há mais tempo que isso: a coleta do ATS pode vir depois. */
	private static final Duration ATS_GRACE = Duration.ofHours(12);

	private static final String SIGNALS = """
			select j.id, j.closed_at is not null as closed, j.link_failures,
			       floor(extract(epoch from (cast(:now as timestamptz) - j.first_seen_at)) / 86400) as age_days, j.raw_description,
			       coalesce(c.kind, 'CONTRATANTE') as company_kind, c.ats_type,
			       -- republicações: vagas anteriores da empresa, já fechadas, com título parecido nos últimos 180 dias
			       (select count(*) from job p
			        where p.company_id = j.company_id and p.id <> j.id and p.closed_at is not null
			          and p.duplicate_of_job_id is null and p.first_seen_at < j.first_seen_at
			          and p.first_seen_at >= cast(:now as timestamptz) - interval '180 days'
			          and similarity(lower(p.title), lower(j.title)) > 0.8) as reposts,
			       -- a empresa tem ATS coletado (vagas dele vistas na última semana), a vaga não veio dele
			       -- e não há vaga aberta parecida lá
			       (c.ats_type is not null and j.source not in ('GREENHOUSE', 'LEVER', 'ASHBY')
			        and j.first_seen_at <= :graceLimit
			        and exists (select 1 from job a where a.company_id = j.company_id
			                    and a.source in ('GREENHOUSE', 'LEVER', 'ASHBY') and a.last_seen_at >= cast(:now as timestamptz) - interval '7 days')
			        and not exists (select 1 from job a where a.company_id = j.company_id and a.closed_at is null
			                        and a.source in ('GREENHOUSE', 'LEVER', 'ASHBY')
			                        and (a.id = j.duplicate_of_job_id or a.duplicate_of_job_id = j.id
			                             or similarity(lower(a.title), lower(j.title)) >= 0.6))) as missing_from_ats
			from job j
			left join company c on c.id = j.company_id
			""";

	private final JdbcClient jdbc;

	private final JsonMapper json;

	private final Clock clock;

	LegitimacyAssessor(JdbcClient jdbc, JsonMapper json, Clock clock) {
		this.jdbc = jdbc;
		this.json = json;
		this.clock = clock;
	}

	/** Reavalia uma vaga e grava o resultado. */
	@Transactional
	public Assessment assess(long jobId) {
		Instant now = this.clock.instant();
		return this.jdbc.sql(SIGNALS + " where j.id = :jobId")
			.param("now", Timestamp.from(now))
			.param("graceLimit", Timestamp.from(now.minus(ATS_GRACE)))
			.param("jobId", jobId)
			.query((rs, row) -> new Row(rs.getLong("id"), signals(rs)))
			.optional()
			.map(row -> save(row, now))
			.orElse(null);
	}

	@Scheduled(cron = "${jobhunter.jobs.legitimacy-cron:0 45 6 * * *}", zone = "America/Sao_Paulo")
	@SchedulerLock(name = "job-legitimacy", lockAtMostFor = "PT1H")
	void daily() {
		int assessed = assessOpen();
		log.info("Legitimidade de {} vagas abertas reavaliada.", assessed);
	}

	/** Reavalia todas as vagas abertas que não são cópia de outra. */
	@Transactional
	public int assessOpen() {
		Instant now = this.clock.instant();
		List<Row> rows = this.jdbc.sql(SIGNALS + " where j.closed_at is null and j.duplicate_of_job_id is null")
			.param("now", Timestamp.from(now))
			.param("graceLimit", Timestamp.from(now.minus(ATS_GRACE)))
			.query((rs, row) -> new Row(rs.getLong("id"), signals(rs)))
			.list();
		rows.forEach(row -> save(row, now));
		return rows.size();
	}

	private Assessment save(Row row, Instant now) {
		Assessment assessment = evaluate(row.signals());
		this.jdbc.sql("""
				update job set legitimacy = :legitimacy, legitimacy_reasons = cast(:reasons as jsonb),
				    legitimacy_checked_at = :now
				where id = :id
				""")
			.param("legitimacy", assessment.legitimacy().name())
			.param("reasons", this.json.writeValueAsString(assessment.reasons()))
			.param("now", Timestamp.from(now))
			.param("id", row.id())
			.update();
		return assessment;
	}

	private static Signals signals(java.sql.ResultSet rs) throws java.sql.SQLException {
		String description = rs.getString("raw_description");
		String kind = rs.getString("company_kind");
		return new Signals(rs.getInt("reposts"), rs.getInt("link_failures"), rs.getBoolean("closed"),
				rs.getLong("age_days"), rs.getBoolean("missing_from_ats") ? rs.getString("ats_type") : null,
				"RECRUTAMENTO".equals(kind) && description != null && !mentionsClient(description),
				description != null && description.strip().length() < GENERIC_DESCRIPTION,
				ScamSignals.detect(description));
	}

	static boolean mentionsClient(String description) {
		String text = description.toLowerCase(java.util.Locale.ROOT);
		return text.contains("cliente") || text.contains("client") || text.contains("parceir")
				|| text.contains("partner");
	}

	/**
	 * Pontua os sinais: golpe forte (ou dois fracos) é SUSPEITA; sem pontos, ALTA; 1 ou 2, MEDIA; 3 ou mais, BAIXA.
	 * Salário ausente não pesa.
	 */
	static Assessment evaluate(Signals signals) {
		List<String> reasons = new ArrayList<>();
		List<ScamSignals.Signal> scam = signals.scam();
		scam.forEach(signal -> reasons.add(signal.reason()));
		if (scam.stream().anyMatch(ScamSignals.Signal::strong) || scam.size() >= 2) {
			return new Assessment(Legitimacy.SUSPEITA, reasons);
		}
		int points = 2 * scam.size();
		if (signals.closed()) {
			points += 2;
			reasons.add("A vaga saiu do ar");
		}
		else if (signals.linkFailures() > 0) {
			points += 1;
			reasons.add("O link da vaga falhou na última verificação");
		}
		if (signals.reposts() >= 3) {
			points += 2;
			reasons.add("Republicada " + signals.reposts() + " vezes em 180 dias");
		}
		else if (signals.reposts() > 0) {
			points += 1;
			reasons.add((signals.reposts() == 1) ? "Já foi publicada antes nos últimos 180 dias"
					: "Republicada " + signals.reposts() + " vezes em 180 dias");
		}
		if (signals.ageDays() > 60) {
			points += 2;
			reasons.add("No ar há " + signals.ageDays() + " dias");
		}
		else if (signals.ageDays() > 30) {
			points += 1;
			reasons.add("No ar há " + signals.ageDays() + " dias");
		}
		if (signals.missingFromAts() != null) {
			points += 2;
			reasons.add("A empresa publica as vagas no " + atsName(signals.missingFromAts()) + " e esta não aparece lá");
		}
		if (signals.agencyWithoutClient()) {
			points += 1;
			reasons.add("Agência de recrutamento sem citar o cliente");
		}
		if (signals.genericDescription()) {
			points += 1;
			reasons.add("Descrição curta e genérica");
		}
		Legitimacy legitimacy = (points == 0) ? Legitimacy.ALTA : (points <= 2) ? Legitimacy.MEDIA : Legitimacy.BAIXA;
		return new Assessment(legitimacy, reasons);
	}

	private static String atsName(String atsType) {
		String lower = atsType.toLowerCase(java.util.Locale.ROOT);
		return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
	}

	/**
	 * @param missingFromAts o ATS da empresa onde a vaga não aparece; nulo quando não se aplica
	 */
	record Signals(int reposts, int linkFailures, boolean closed, long ageDays, @Nullable String missingFromAts,
			boolean agencyWithoutClient, boolean genericDescription, List<ScamSignals.Signal> scam) {
	}

	public record Assessment(Legitimacy legitimacy, List<String> reasons) {
	}

	private record Row(long id, Signals signals) {
	}

}
