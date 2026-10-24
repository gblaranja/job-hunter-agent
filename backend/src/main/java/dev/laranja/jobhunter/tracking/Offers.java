package dev.laranja.jobhunter.tracking;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import dev.laranja.jobhunter.jobs.WorkModel;
import dev.laranja.jobhunter.shared.NotFoundException;
import dev.laranja.jobhunter.shared.jdbc.JdbcRows;
import dev.laranja.jobhunter.shared.money.Pay;
import dev.laranja.jobhunter.shared.money.PayPeriod;
import dev.laranja.jobhunter.shared.money.PayRegime;
import dev.laranja.jobhunter.shared.money.SalaryEquivalent;
import dev.laranja.jobhunter.shared.money.SalaryMath;
import dev.laranja.jobhunter.tracking.internal.NegotiationDrafter;
import org.jspecify.annotations.Nullable;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Propostas recebidas numa candidatura: guarda os números, calcula o equivalente anual em reais pelo
 * {@link SalaryMath} para comparar CLT e PJ e escreve o rascunho da negociação só com os números informados.
 */
@Service
@Transactional
public class Offers {

	private static final String SELECT = """
			select o.*, a.job_id, coalesce(s.title, j.title) as title, coalesce(s.company, j.company) as company
			from offer o
			join application a on a.id = o.application_id
			join job j on j.id = a.job_id
			left join job_snapshot s on s.application_id = a.id
			""";

	private final JdbcClient jdbc;

	private final SalaryMath salaries;

	private final NegotiationDrafter drafter;

	private final Clock clock;

	Offers(JdbcClient jdbc, SalaryMath salaries, NegotiationDrafter drafter, Clock clock) {
		this.jdbc = jdbc;
		this.salaries = salaries;
		this.drafter = drafter;
		this.clock = clock;
	}

	public OfferView create(long applicationId, OfferCommand command) {
		requireApplication(applicationId);
		Instant now = this.clock.instant();
		OfferDecision decision = (command.decision() != null) ? command.decision() : OfferDecision.PENDENTE;
		long id = this.jdbc.sql("""
				insert into offer (application_id, regime, base_salary, currency, period, annual_bonus, monthly_benefits,
				                   benefits_notes, equity, work_model, start_date, respond_by, decision, decided_at, notes,
				                   created_at, updated_at)
				values (:applicationId, :regime, :baseSalary, :currency, :period, :annualBonus, :monthlyBenefits,
				        :benefitsNotes, :equity, :workModel, :startDate, :respondBy, :decision, :decidedAt, :notes, :now, :now)
				returning id
				""")
			.paramSource(params(command, decision, isFinal(decision) ? now : null)
				.addValue("applicationId", applicationId)
				.addValue("now", Timestamp.from(now)))
			.query(Long.class)
			.single();
		return get(id);
	}

	public OfferView update(long id, OfferCommand command) {
		OfferView current = get(id);
		Instant now = this.clock.instant();
		OfferDecision decision = (command.decision() != null) ? command.decision() : current.decision();
		Instant decidedAt = !isFinal(decision) ? null
				: (decision == current.decision() && current.decidedAt() != null) ? current.decidedAt() : now;
		this.jdbc.sql("""
				update offer set regime = :regime, base_salary = :baseSalary, currency = :currency, period = :period,
				    annual_bonus = :annualBonus, monthly_benefits = :monthlyBenefits, benefits_notes = :benefitsNotes,
				    equity = :equity, work_model = :workModel, start_date = :startDate, respond_by = :respondBy,
				    decision = :decision, decided_at = :decidedAt, notes = :notes, updated_at = :now
				where id = :id
				""")
			.paramSource(params(command, decision, decidedAt).addValue("id", id).addValue("now", Timestamp.from(now)))
			.update();
		return get(id);
	}

	public void delete(long id) {
		if (this.jdbc.sql("delete from offer where id = :id").param("id", id).update() == 0) {
			throw NotFoundException.of("Proposta", id);
		}
	}

	@Transactional(readOnly = true)
	public OfferView get(long id) {
		return this.jdbc.sql(SELECT + " where o.id = :id")
			.param("id", id)
			.query((rs, row) -> toView(rs))
			.optional()
			.orElseThrow(() -> NotFoundException.of("Proposta", id));
	}

	@Transactional(readOnly = true)
	public List<OfferView> forApplication(long applicationId) {
		requireApplication(applicationId);
		return this.jdbc.sql(SELECT + " where o.application_id = :applicationId order by o.created_at, o.id")
			.param("applicationId", applicationId)
			.query((rs, row) -> toView(rs))
			.list();
	}

	/** Todas as propostas, as em aberto primeiro, para a comparação lado a lado. */
	@Transactional(readOnly = true)
	public List<OfferView> all() {
		return this.jdbc.sql(SELECT + """
				 order by case o.decision when 'NEGOCIANDO' then 0 when 'PENDENTE' then 1 when 'ACEITA' then 2 else 3 end,
				          o.created_at desc, o.id desc
				""").query((rs, row) -> toView(rs)).list();
	}

	/**
	 * Rascunho da resposta pedindo um valor melhor. Usa só os números desta proposta, das suas outras propostas em
	 * aberto e o valor que você pediu. Roda fora de transação para não segurar a conexão durante a chamada à IA.
	 */
	@Transactional(propagation = Propagation.NOT_SUPPORTED)
	public NegotiationDraft negotiationDraft(long id, NegotiationDraft.Request request) {
		OfferView offer = get(id);
		List<OfferView> others = all().stream()
			.filter(other -> other.id() != id && other.applicationId() != offer.applicationId())
			.filter(other -> other.decision() == OfferDecision.PENDENTE || other.decision() == OfferDecision.NEGOCIANDO)
			.toList();
		return this.drafter.draft(offer, others, request);
	}

	private OfferView toView(ResultSet rs) throws SQLException {
		PayRegime regime = PayRegime.valueOf(rs.getString("regime"));
		BigDecimal base = rs.getBigDecimal("base_salary");
		String currency = rs.getString("currency");
		PayPeriod period = PayPeriod.valueOf(rs.getString("period"));
		BigDecimal bonus = rs.getBigDecimal("annual_bonus");
		BigDecimal benefits = rs.getBigDecimal("monthly_benefits");
		Optional<SalaryEquivalent> equivalent = this.salaries
			.equivalent(new Pay(base, currency, period, regime, benefits, bonus));
		return new OfferView(rs.getLong("id"), rs.getLong("application_id"), rs.getLong("job_id"), rs.getString("title"),
				rs.getString("company"), regime, base, currency, period, bonus, benefits, rs.getString("benefits_notes"),
				rs.getString("equity"), JdbcRows.nullableEnum(rs, "work_model", WorkModel.class),
				localDate(rs, "start_date"), localDate(rs, "respond_by"),
				OfferDecision.valueOf(rs.getString("decision")), JdbcRows.instant(rs, "decided_at"),
				rs.getString("notes"), equivalent.map(SalaryEquivalent::annualBrl).orElse(null),
				equivalent.map(SalaryEquivalent::monthlyBrl).orElse(null),
				equivalent.flatMap(e -> Optional.ofNullable(e.exchangeRate())).map(rate -> rate.brl()).orElse(null),
				JdbcRows.instant(rs, "created_at"));
	}

	private static MapSqlParameterSource params(OfferCommand command,
			OfferDecision decision, @Nullable Instant decidedAt) {
		return new MapSqlParameterSource()
			.addValue("regime", command.regime().name())
			.addValue("baseSalary", command.baseSalary())
			.addValue("currency", (command.currency() != null && !command.currency().isBlank())
					? command.currency().strip().toUpperCase(Locale.ROOT) : "BRL")
			.addValue("period", ((command.period() != null) ? command.period() : PayPeriod.MENSAL).name())
			.addValue("annualBonus", command.annualBonus())
			.addValue("monthlyBenefits", command.monthlyBenefits())
			.addValue("benefitsNotes", blankToNull(command.benefitsNotes()))
			.addValue("equity", blankToNull(command.equity()))
			.addValue("workModel", (command.workModel() != null) ? command.workModel().name() : null)
			.addValue("startDate", (command.startDate() != null) ? Date.valueOf(command.startDate()) : null)
			.addValue("respondBy", (command.respondBy() != null) ? Date.valueOf(command.respondBy()) : null)
			.addValue("decision", decision.name())
			.addValue("decidedAt", (decidedAt != null) ? Timestamp.from(decidedAt) : null)
			.addValue("notes", blankToNull(command.notes()));
	}

	private void requireApplication(long applicationId) {
		boolean exists = this.jdbc.sql("select exists (select 1 from application where id = :id)")
			.param("id", applicationId)
			.query(Boolean.class)
			.single();
		if (!exists) {
			throw NotFoundException.of("Candidatura", applicationId);
		}
	}

	private static boolean isFinal(OfferDecision decision) {
		return decision == OfferDecision.ACEITA || decision == OfferDecision.RECUSADA;
	}

	private static @Nullable LocalDate localDate(ResultSet rs, String column) throws SQLException {
		Date value = rs.getDate(column);
		return (value != null) ? value.toLocalDate() : null;
	}

	private static @Nullable String blankToNull(@Nullable String value) {
		return (value == null || value.isBlank()) ? null : value.strip();
	}

}
