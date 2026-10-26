package dev.laranja.jobhunter.notifications.internal;

import java.util.stream.Collectors;

import dev.laranja.jobhunter.analysis.JobAnalyzed;
import dev.laranja.jobhunter.companies.CompanyAlertRaised;
import dev.laranja.jobhunter.jobs.JobCatalog;
import dev.laranja.jobhunter.jobs.JobView;
import dev.laranja.jobhunter.niches.CompanySeedCompleted;
import dev.laranja.jobhunter.notifications.Notifications;
import dev.laranja.jobhunter.recognition.RecognitionReviewNeeded;
import dev.laranja.jobhunter.shared.NotificationRequested;
import dev.laranja.jobhunter.tailoring.TailoredResumeReady;
import dev.laranja.jobhunter.tracking.FollowUpsDue;
import dev.laranja.jobhunter.tracking.NextActionKind;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/** Transforma eventos dos outros módulos em notificações. */
@Component
class NotificationTriggers {

	/** Vagas a partir deste score viram notificação. */
	static final int HIGH_SCORE = 75;

	/** Vaga de empresa-alvo a partir deste score vai ao celular (VAGA_ALVO). */
	static final int TARGET_SCORE = 60;

	/** Empresas citadas no lembrete de follow-up; as demais entram como "e mais N". */
	private static final int LISTED = 5;

	private final Notifications notifications;

	private final JobCatalog jobs;

	private final JdbcClient jdbc;

	NotificationTriggers(Notifications notifications, JobCatalog jobs, JdbcClient jdbc) {
		this.notifications = notifications;
		this.jobs = jobs;
		this.jdbc = jdbc;
	}

	/**
	 * Vaga aderente: de empresa-alvo a partir de {@value #TARGET_SCORE} (VAGA_ALVO, que vai ao Telegram); das demais,
	 * a partir de {@value #HIGH_SCORE} (VAGA_ADERENTE, só no sino).
	 */
	@ApplicationModuleListener
	void on(JobAnalyzed event) {
		if (event.matchScore() == null || event.matchScore() < TARGET_SCORE) {
			return;
		}
		JobView job = this.jobs.get(event.jobId());
		if (isOpenAtTargetCompany(job.id())) {
			this.notifications.notify("VAGA_ALVO",
					"Empresa-alvo: " + job.title() + " (" + event.matchScore() + "% de aderência)",
					job.company() + ((job.location() != null) ? " · " + job.location() : ""), "/jobs/" + job.id(),
					"vaga-alvo-" + job.id());
			return;
		}
		if (event.matchScore() < HIGH_SCORE) {
			return;
		}
		this.notifications.notify("VAGA_ADERENTE", "Vaga com " + event.matchScore() + "% de aderência: " + job.title(),
				job.company(), "/jobs/" + job.id(), "vaga-aderente-" + job.id());
	}

	/** Pedido de notificação de um módulo que não depende de {@code notifications}. */
	@ApplicationModuleListener
	void on(NotificationRequested event) {
		this.notifications.notify(event.type(), event.title(), event.body(), event.link(), event.dedupKey());
	}

	@ApplicationModuleListener
	void on(TailoredResumeReady event) {
		JobView job = this.jobs.get(event.jobId());
		this.notifications.notify("CURRICULO_ADAPTADO", "Currículo adaptado para " + job.title(),
				job.company() + " · " + event.coveredKeywords() + " de " + event.keywords()
						+ " palavras-chave da vaga no texto",
				"/jobs/" + job.id(), "curriculo-adaptado-" + event.tailoredResumeId());
	}

	@ApplicationModuleListener
	void on(CompanyAlertRaised event) {
		this.notifications.notify(event.type(), event.title(), event.body(), "/companies/" + event.companyId(),
				event.dedupKey());
	}

	@ApplicationModuleListener
	void on(RecognitionReviewNeeded event) {
		String level = switch (event.level()) {
			case RANKING_TI -> "ranking GPTW TI";
			case RANKING -> "ranking GPTW";
			default -> "certificação GPTW";
		};
		this.notifications.notify("SELO_REVISAR", "Confirme o selo de " + event.companyName(),
				"Encontrei " + level + " " + event.year() + ", mas com confiança baixa ou nome parecido com outra empresa.",
				"/companies?review=gptw", "selo-revisar-" + event.recognitionId());
	}

	@ApplicationModuleListener
	void on(CompanySeedCompleted event) {
		if (event.error() != null) {
			this.notifications.notify("IMPORTACAO_FALHOU", "A importação de " + event.label() + " falhou", event.error(),
					"/settings", null);
			return;
		}
		this.notifications.notify("IMPORTACAO_CONCLUIDA", "Importação de " + event.label() + " concluída",
				event.processed() + " empresas lidas, " + event.created() + " novas.", "/niches", null);
	}

	// Vaga aberta e canônica de empresa marcada como ALVO, sem sinal de golpe (leitura direta de jobs e companies).
	private boolean isOpenAtTargetCompany(long jobId) {
		return this.jdbc.sql("""
				select count(*) from job j join company c on c.id = j.company_id
				where j.id = :jobId and c.watch_status = 'ALVO' and j.closed_at is null and j.duplicate_of_job_id is null
				  and coalesce(j.legitimacy, 'ALTA') <> 'SUSPEITA'
				""")
			.param("jobId", jobId)
			.query(Long.class)
			.single() > 0;
	}

	@ApplicationModuleListener
	void on(FollowUpsDue event) {
		int total = event.actions().size();
		long followUps = event.actions().stream().filter(action -> action.kind() == NextActionKind.FOLLOW_UP).count();
		String title;
		if (followUps == total) {
			title = (total == 1) ? "1 follow-up para fazer hoje" : total + " follow-ups para fazer hoje";
		}
		else {
			title = (total == 1) ? "1 próxima ação vencida nas candidaturas"
					: total + " próximas ações vencidas nas candidaturas";
		}
		String body = event.actions()
			.stream()
			.limit(LISTED)
			.map(action -> nextActionLabel(action.kind()) + ": " + action.company())
			.collect(Collectors.joining(" · ")) + ((total > LISTED) ? " e mais " + (total - LISTED) : "");
		this.notifications.notify("FOLLOW_UP", title, body, "/board", "follow-up-" + event.day());
	}

	private static String nextActionLabel(NextActionKind kind) {
		return switch (kind) {
			case FOLLOW_UP -> "Follow-up";
			case RESPONDER -> "Responder";
			case ENTREVISTA -> "Entrevista";
			case OUTRO -> "Próxima ação";
		};
	}

}
