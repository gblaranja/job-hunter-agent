package dev.laranja.jobhunter.tracking.internal;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Objects;
import java.util.Set;

import dev.laranja.jobhunter.shared.BusinessRuleException;
import dev.laranja.jobhunter.tracking.ApplicationChannel;
import dev.laranja.jobhunter.tracking.ApplicationStatus;
import dev.laranja.jobhunter.tracking.EventOrigin;
import dev.laranja.jobhunter.tracking.NextActionKind;
import dev.laranja.jobhunter.tracking.ResponseChannel;
import dev.laranja.jobhunter.tracking.ResponseType;
import dev.laranja.jobhunter.tracking.TrackingProperties.FollowUp;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/**
 * A candidatura a uma vaga. Guarda só o estado atual; cada mudança devolve um {@link ApplicationEvent} para o
 * histórico, de onde saem as métricas de conversão.
 * <p>
 * Follow-up: ao chegar em Aplicada sem próxima ação, o follow-up é agendado sozinho ({@code nextActionAuto}). Qualquer
 * retorno da empresa ou a saída de Aplicada cancela o automático; recusa, desistência e vaga encerrada limpam qualquer
 * próxima ação. Cada follow-up enviado reagenda o próximo até o máximo configurado.
 */
@Entity
@Table(name = "application")
public class Application {

	/** Dia em que você vê a próxima ação; a data escolhida na tela vale para o dia inteiro. */
	private static final ZoneId ZONE = ZoneId.of("America/Sao_Paulo");

	/** Etapas que encerram a candidatura sem nada mais a fazer: limpam qualquer próxima ação. */
	private static final Set<ApplicationStatus> CLOSED = Set.of(ApplicationStatus.RECUSADA, ApplicationStatus.DESISTI,
			ApplicationStatus.ENCERRADA);

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	private long jobId;

	@Enumerated(EnumType.STRING)
	private ApplicationStatus status;

	private Instant appliedAt;

	@Enumerated(EnumType.STRING)
	private ApplicationChannel channel;

	private Long resumeVersionId;

	private String notes;

	private Instant nextActionAt;

	@Enumerated(EnumType.STRING)
	private NextActionKind nextActionKind;

	private int followUpsSent;

	private boolean nextActionAuto;

	private Instant createdAt;

	private Instant updatedAt;

	@Version
	private long version;

	protected Application() {
	}

	public static Application start(long jobId, ApplicationStatus status, Long resumeVersionId,
			ApplicationChannel channel, Instant appliedAt, String notes, FollowUp policy, Instant now) {
		if (status == ApplicationStatus.NOVA) {
			throw new BusinessRuleException("Uma candidatura começa em Interesse ou numa etapa posterior.");
		}
		requireResumeWhenApplied(status, resumeVersionId);
		Application application = new Application();
		application.jobId = jobId;
		application.status = status;
		application.resumeVersionId = resumeVersionId;
		application.channel = channel;
		application.notes = blankToNull(notes);
		if (status.requiresResume()) {
			application.appliedAt = (appliedAt != null) ? appliedAt : now;
		}
		application.createdAt = now;
		application.updatedAt = now;
		application.scheduleFirstFollowUp(policy);
		return application;
	}

	/** Evento inicial do histórico; chame depois que a candidatura tiver id. */
	public ApplicationEvent creationEvent() {
		return ApplicationEvent.statusChange(this.id, null, this.status, EventOrigin.MANUAL, this.notes,
				this.createdAt);
	}

	/**
	 * Move a candidatura para outra etapa.
	 * @param resumeVersionId currículo enviado; obrigatório a partir de APLICADA se ainda não houver um registrado
	 * @return o evento a ser gravado no histórico
	 */
	public ApplicationEvent moveTo(ApplicationStatus target, Long resumeVersionId, String note, EventOrigin origin,
			FollowUp policy, Instant now) {
		if (target == this.status) {
			throw new BusinessRuleException("A candidatura já está em " + target + ".");
		}
		if (target == ApplicationStatus.NOVA) {
			throw new BusinessRuleException("Uma candidatura não volta para Nova.");
		}
		if (this.status.isTerminal() && (note == null || note.isBlank())) {
			throw new BusinessRuleException("Para reabrir uma candidatura encerrada, explique o motivo na nota.");
		}
		Long resolvedResume = (resumeVersionId != null) ? resumeVersionId : this.resumeVersionId;
		requireResumeWhenApplied(target, resolvedResume);

		ApplicationEvent event = ApplicationEvent.statusChange(this.id, this.status, target, origin,
				blankToNull(note), now);
		this.status = target;
		this.resumeVersionId = resolvedResume;
		if (target.requiresResume() && this.appliedAt == null) {
			this.appliedAt = now;
		}
		adjustNextActionAfterMove(policy);
		this.updatedAt = now;
		return event;
	}

	/**
	 * Registra um retorno da empresa e, se fizer sentido, move a candidatura para a etapa correspondente. O
	 * movimento é ignorado (fica só o registro) quando a etapa é a mesma, a candidatura está encerrada ou falta o
	 * currículo enviado.
	 * @param target etapa sugerida pelo tipo de retorno; nulo para só registrar
	 */
	public ApplicationEvent recordResponse(ResponseType type, ResponseChannel channel, String note,
			ApplicationStatus target, EventOrigin origin, Long emailMessageId, Instant now) {
		boolean move = target != null && target != this.status && target != ApplicationStatus.NOVA
				&& !this.status.isTerminal() && (!target.requiresResume() || this.resumeVersionId != null);
		ApplicationStatus from = move ? this.status : null;
		ApplicationStatus to = move ? target : null;
		if (move) {
			this.status = target;
			if (target.requiresResume() && this.appliedAt == null) {
				this.appliedAt = now;
			}
		}
		// A empresa respondeu: o follow-up automático perde o sentido. Um pedido de informação vira "responder".
		if (this.nextActionAuto) {
			clearNextAction();
		}
		if (CLOSED.contains(this.status)) {
			clearNextAction();
		}
		else if (type == ResponseType.PEDIDO_INFORMACAO && this.nextActionAt == null) {
			schedule(now.plus(Duration.ofDays(1)), NextActionKind.RESPONDER, true);
		}
		this.updatedAt = now;
		return ApplicationEvent.response(this.id, from, to, type, channel, origin, emailMessageId, blankToNull(note), now);
	}

	public void changeResume(long resumeVersionId, Instant now) {
		this.resumeVersionId = resumeVersionId;
		this.updatedAt = now;
	}

	/**
	 * Notas e próxima ação editadas por você. A mesma data (no dia) mantém o agendamento como está; uma data nova vira
	 * uma ação sua, que nenhum retorno cancela.
	 * @param kind tipo da próxima ação; sem valor, mantém o atual ou usa OUTRO
	 */
	public void updateDetails(String notes, Instant nextActionAt, NextActionKind kind, Instant now) {
		this.notes = blankToNull(notes);
		boolean sameDay = nextActionAt != null && this.nextActionAt != null
				&& day(nextActionAt).equals(day(this.nextActionAt));
		if (nextActionAt == null) {
			clearNextAction();
		}
		else if (!sameDay) {
			schedule(nextActionAt, (kind != null) ? kind : Objects.requireNonNullElse(this.nextActionKind,
					NextActionKind.OUTRO), false);
		}
		else if (kind != null && kind != this.nextActionKind) {
			this.nextActionKind = kind;
			this.nextActionAuto = false;
		}
		this.updatedAt = now;
	}

	/** Agenda uma próxima ação sua (não automática), sem mexer nas notas. */
	public void scheduleNextAction(Instant at, NextActionKind kind, Instant now) {
		schedule(at, kind, false);
		this.updatedAt = now;
	}

	/**
	 * Você mandou um follow-up: conta o envio e agenda o próximo, até o máximo configurado. Depois do último, a
	 * candidatura para de pedir follow-up.
	 */
	public void registerFollowUp(FollowUp policy, Instant now) {
		if (this.status.isTerminal()) {
			throw new BusinessRuleException("A candidatura está encerrada; não há follow-up a registrar.");
		}
		this.followUpsSent++;
		boolean followUpPending = this.nextActionAt == null || this.nextActionKind == NextActionKind.FOLLOW_UP;
		if (followUpPending) {
			if (this.followUpsSent < policy.max()) {
				schedule(now.plus(policy.interval()), NextActionKind.FOLLOW_UP, true);
			}
			else {
				clearNextAction();
			}
		}
		this.updatedAt = now;
	}

	private void scheduleFirstFollowUp(FollowUp policy) {
		if (this.status == ApplicationStatus.APLICADA && this.nextActionAt == null && this.appliedAt != null
				&& this.followUpsSent < policy.max()) {
			schedule(this.appliedAt.plus(policy.first()), NextActionKind.FOLLOW_UP, true);
		}
	}

	private void adjustNextActionAfterMove(FollowUp policy) {
		if (CLOSED.contains(this.status)) {
			clearNextAction();
		}
		else if (this.status == ApplicationStatus.APLICADA) {
			scheduleFirstFollowUp(policy);
		}
		else if (this.nextActionAuto) {
			clearNextAction();
		}
	}

	private void schedule(Instant at, NextActionKind kind, boolean auto) {
		this.nextActionAt = at;
		this.nextActionKind = kind;
		this.nextActionAuto = auto;
	}

	private void clearNextAction() {
		this.nextActionAt = null;
		this.nextActionKind = null;
		this.nextActionAuto = false;
	}

	private static LocalDate day(Instant instant) {
		return LocalDate.ofInstant(instant, ZONE);
	}

	private static void requireResumeWhenApplied(ApplicationStatus status, Long resumeVersionId) {
		if (status.requiresResume() && resumeVersionId == null) {
			throw new BusinessRuleException("Escolha a versão do currículo enviada para mover a candidatura para "
					+ status + ".");
		}
	}

	private static String blankToNull(String value) {
		return (value == null || value.isBlank()) ? null : value.strip();
	}

	public Long getId() {
		return this.id;
	}

	public long getJobId() {
		return this.jobId;
	}

	public ApplicationStatus getStatus() {
		return this.status;
	}

	public Long getResumeVersionId() {
		return this.resumeVersionId;
	}

	public Instant getAppliedAt() {
		return this.appliedAt;
	}

	public Instant getNextActionAt() {
		return this.nextActionAt;
	}

	public NextActionKind getNextActionKind() {
		return this.nextActionKind;
	}

	public boolean isNextActionAuto() {
		return this.nextActionAuto;
	}

	public int getFollowUpsSent() {
		return this.followUpsSent;
	}

}
