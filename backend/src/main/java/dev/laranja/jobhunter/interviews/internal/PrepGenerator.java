package dev.laranja.jobhunter.interviews.internal;

import java.sql.Timestamp;
import java.time.Clock;

import dev.laranja.jobhunter.interviews.InterviewPrepContent;
import dev.laranja.jobhunter.interviews.InterviewPrepView;
import dev.laranja.jobhunter.interviews.InterviewPreps;
import dev.laranja.jobhunter.shared.NotificationRequested;
import dev.laranja.jobhunter.shared.ai.LlmException;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.json.JsonMapper;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Gera a preparação pedida. A chamada à IA fica fora de transação; uma falha dela cai nas regras e fica registrada em
 * {@code last_error}. Ao terminar, pede a notificação "Preparação pronta" (sino e, se configurado, Telegram).
 */
@Component
class PrepGenerator {

	static final String RULES_VERSION = "regras|prepare-interview@v1";

	private static final Logger log = LoggerFactory.getLogger(PrepGenerator.class);

	private final InterviewPreps preps;

	private final PrepInputsReader inputs;

	private final PrepWriter writer;

	private final JdbcClient jdbc;

	private final JsonMapper json;

	private final ApplicationEventPublisher events;

	private final TransactionTemplate transactions;

	private final Clock clock;

	PrepGenerator(InterviewPreps preps, PrepInputsReader inputs, PrepWriter writer, JdbcClient jdbc, JsonMapper json,
			ApplicationEventPublisher events, PlatformTransactionManager transactionManager, Clock clock) {
		this.preps = preps;
		this.inputs = inputs;
		this.writer = writer;
		this.jdbc = jdbc;
		this.json = json;
		this.events = events;
		this.transactions = new TransactionTemplate(transactionManager);
		this.clock = clock;
	}

	void generate(long prepId) {
		InterviewPrepView pending = this.preps.get(prepId);
		if (!"GERANDO".equals(pending.status())) {
			return;
		}
		try {
			PrepInputs in = this.inputs.read(pending.applicationId(), pending.stage());
			InterviewPrepContent content = null;
			String error = null;
			if (this.writer.enabled()) {
				try {
					content = this.writer.write(in);
				}
				catch (LlmException ex) {
					log.warn("Preparação {} pelas regras: {}", prepId, ex.getMessage());
					error = ex.getMessage();
				}
			}
			boolean ai = content != null;
			InterviewPrepContent result = ai ? content : PrepRules.build(in);
			String finalError = error;
			this.transactions.executeWithoutResult(status -> {
				save(prepId, result, ai ? "IA" : "REGRAS", ai ? this.writer.version() : RULES_VERSION, finalError);
				this.events.publishEvent(new NotificationRequested("PREPARACAO_PRONTA",
						"Preparação pronta: " + in.title() + " · " + in.company(),
						result.questions().size() + " perguntas prováveis e o que revisar antes da "
								+ ((in.stage() == dev.laranja.jobhunter.tracking.ApplicationStatus.TESTE_TECNICO)
										? "revisão do teste." : "entrevista."),
						"/jobs/" + in.jobId(), "preparacao-" + prepId + "-" + this.clock.instant().toEpochMilli()));
			});
		}
		catch (RuntimeException ex) {
			log.warn("Não foi possível gerar a preparação {}: {}", prepId, ex.getMessage());
			this.jdbc.sql("update interview_prep set status = 'FALHOU', last_error = :error, updated_at = :now where id = :id")
				.param("id", prepId)
				.param("error", String.valueOf(ex.getMessage()))
				.param("now", Timestamp.from(this.clock.instant()))
				.update();
		}
	}

	private void save(long id, InterviewPrepContent content, String source, String version, @Nullable String error) {
		this.jdbc.sql("""
				update interview_prep set status = 'PRONTO', content = cast(:content as jsonb), source = :source,
				    model_version = :version, last_error = :error, updated_at = :now
				where id = :id
				""")
			.param("id", id)
			.param("content", this.json.writeValueAsString(content))
			.param("source", source)
			.param("version", version)
			.param("error", error)
			.param("now", Timestamp.from(this.clock.instant()))
			.update();
	}

}
