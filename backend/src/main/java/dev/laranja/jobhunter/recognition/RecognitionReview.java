package dev.laranja.jobhunter.recognition;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import dev.laranja.jobhunter.recognition.internal.CompanyMatcher;
import dev.laranja.jobhunter.shared.BusinessRuleException;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Fila de revisão dos selos e o histórico de cada empresa. O selo da fila só aparece depois da sua confirmação. */
@Service
@Transactional
public class RecognitionReview {

	private static final ZoneId ZONE = ZoneId.of("America/Sao_Paulo");

	private final JdbcClient jdbc;

	private final CompanyMatcher matcher;

	private final ApplicationEventPublisher events;

	private final Clock clock;

	RecognitionReview(JdbcClient jdbc, CompanyMatcher matcher, ApplicationEventPublisher events, Clock clock) {
		this.jdbc = jdbc;
		this.matcher = matcher;
		this.events = events;
		this.clock = clock;
	}

	@Transactional(readOnly = true)
	public List<RecognitionView> pending() {
		LocalDate today = today();
		return this.jdbc.sql(RecognitionQueries.SELECT + """
				where r.review_status = 'PENDENTE'
				order by r.checked_at desc
				""")
			.query((rs, row) -> RecognitionQueries.map(rs, today,
					this.matcher.homonyms(rs.getLong("company_id"), rs.getString("company_normalized_name"))))
			.list();
	}

	@Transactional(readOnly = true)
	public List<RecognitionView> history(long companyId) {
		LocalDate today = today();
		return this.jdbc.sql(RecognitionQueries.SELECT + """
				where r.company_id = :companyId and r.review_status <> 'REJEITADO'
				order by r.year desc nulls last, r.checked_at desc
				""").param("companyId", companyId).query((rs, row) -> RecognitionQueries.map(rs, today, List.of())).list();
	}

	/** Confirma o selo: passa a aparecer, verificado por você. */
	public RecognitionView confirm(long id) {
		RecognitionView recognition = pendingOrFail(id);
		this.jdbc.sql("update company_recognition set review_status = 'CONFIRMADO', verified_by = 'USUARIO' where id = :id")
			.param("id", id)
			.update();
		this.events.publishEvent(new RecognitionChanged(recognition.companyId()));
		return RecognitionQueries.get(this.jdbc, id, today());
	}

	/** O selo era de outra empresa (homônimo) ou a evidência não comprova: sai da fila e não aparece. */
	public void reject(long id) {
		RecognitionView recognition = pendingOrFail(id);
		this.jdbc.sql("update company_recognition set review_status = 'REJEITADO', verified_by = 'USUARIO' where id = :id")
			.param("id", id)
			.update();
		this.events.publishEvent(new RecognitionChanged(recognition.companyId()));
	}

	private RecognitionView pendingOrFail(long id) {
		RecognitionView recognition = RecognitionQueries.get(this.jdbc, id, today());
		if (!"PENDENTE".equals(recognition.reviewStatus())) {
			throw new BusinessRuleException("Este selo já foi revisado.");
		}
		return recognition;
	}

	private LocalDate today() {
		return LocalDate.now(this.clock.withZone(ZONE));
	}

}
