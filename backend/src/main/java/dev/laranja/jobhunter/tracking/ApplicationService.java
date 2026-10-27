package dev.laranja.jobhunter.tracking;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import dev.laranja.jobhunter.inbox.Mailbox;
import dev.laranja.jobhunter.jobs.JobCatalog;
import dev.laranja.jobhunter.jobs.JobOrigin;
import dev.laranja.jobhunter.jobs.JobView;
import dev.laranja.jobhunter.jobs.ManualJobCommand;
import dev.laranja.jobhunter.resumes.ResumeCatalog;
import dev.laranja.jobhunter.shared.BusinessRuleException;
import dev.laranja.jobhunter.shared.ConflictException;
import dev.laranja.jobhunter.shared.NotFoundException;
import dev.laranja.jobhunter.shared.storage.DownloadLink;
import dev.laranja.jobhunter.shared.storage.ObjectStorage;
import dev.laranja.jobhunter.tracking.internal.Application;
import dev.laranja.jobhunter.tracking.internal.ApplicationEventRepository;
import dev.laranja.jobhunter.tracking.internal.ApplicationRepository;
import dev.laranja.jobhunter.tracking.internal.JobSnapshot;
import dev.laranja.jobhunter.tracking.internal.JobSnapshotRepository;

import org.jspecify.annotations.Nullable;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Ciclo de vida das candidaturas: cria, move no funil, registra o currículo enviado e congela a vaga no
 * momento da aplicação.
 */
@Service
@Transactional
@EnableConfigurationProperties(TrackingProperties.class)
public class ApplicationService {

	private static final Duration PDF_LINK_TTL = Duration.ofMinutes(5);

	private static final long MAX_PDF_BYTES = 10L * 1024 * 1024;

	private final ApplicationRepository applications;

	private final ApplicationEventRepository history;

	private final JobSnapshotRepository snapshots;

	private final JobCatalog jobs;

	private final ResumeCatalog resumes;

	private final ObjectStorage storage;

	private final ApplicationEventPublisher events;

	private final JdbcClient jdbc;

	private final TrackingProperties.FollowUp followUp;

	private final Mailbox mailbox;

	private final Clock clock;

	ApplicationService(ApplicationRepository applications, ApplicationEventRepository history,
			JobSnapshotRepository snapshots, JobCatalog jobs, ResumeCatalog resumes, ObjectStorage storage,
			ApplicationEventPublisher events, JdbcClient jdbc, TrackingProperties properties, Mailbox mailbox,
			Clock clock) {
		this.applications = applications;
		this.history = history;
		this.snapshots = snapshots;
		this.jobs = jobs;
		this.resumes = resumes;
		this.storage = storage;
		this.events = events;
		this.jdbc = jdbc;
		this.followUp = properties.followUp();
		this.mailbox = mailbox;
		this.clock = clock;
	}

	public long start(long jobId, ApplicationCommands.Start command) {
		JobView job = this.jobs.get(jobId);
		this.applications.findByJobId(jobId).ifPresent(existing -> {
			throw new ConflictException("Essa vaga já tem uma candidatura.", existing.getId());
		});
		if (command.resumeVersionId() != null) {
			this.resumes.requireSelectable(command.resumeVersionId());
		}
		Instant now = this.clock.instant();
		Application application = this.applications.save(Application.start(jobId, command.status(),
				command.resumeVersionId(), command.channel(), command.appliedAt(), command.notes(), this.followUp, now));
		this.history.save(application.creationEvent());
		captureSnapshotIfApplied(application, job, now);
		closeJobIfEnded(application);
		this.events.publishEvent(new ApplicationStatusChanged(application.getId(), jobId, null,
				application.getStatus(), EventOrigin.MANUAL, now));
		return application.getId();
	}

	public void changeStatus(long applicationId, ApplicationCommands.ChangeStatus command) {
		Application application = require(applicationId);
		if (command.resumeVersionId() != null) {
			this.resumes.requireSelectable(command.resumeVersionId());
		}
		ApplicationStatus from = application.getStatus();
		Instant now = this.clock.instant();
		this.history.save(application.moveTo(command.status(), command.resumeVersionId(), command.note(),
				EventOrigin.MANUAL, this.followUp, now));
		captureSnapshotIfApplied(application, null, now);
		closeJobIfEnded(application);
		this.events.publishEvent(new ApplicationStatusChanged(applicationId, application.getJobId(), from,
				application.getStatus(), EventOrigin.MANUAL, now));
	}

	/** Retorno registrado à mão (WhatsApp, mensagem no LinkedIn, ligação), com o movimento opcional do card. */
	public void recordManualResponse(long applicationId, ApplicationCommands.RecordResponse command) {
		Application application = require(applicationId);
		ApplicationStatus target = command.moveStatus() ? command.responseType().targetStatus().orElse(null) : null;
		recordResponse(application, command.responseType(), command.channel(), command.note(), target,
				EventOrigin.MANUAL, null);
	}

	/** Retorno lido de um e-mail com confiança suficiente: registra e move o card para a etapa correspondente. */
	public void recordEmailResponse(long applicationId, ResponseType type, long emailMessageId, String subject) {
		Application application = require(applicationId);
		recordResponse(application, type, ResponseChannel.EMAIL, subject, type.targetStatus().orElse(null),
				EventOrigin.EMAIL, emailMessageId);
	}

	/**
	 * Você corrigiu um e-mail. Se ele já tinha movido um card, o movimento é desfeito com um evento compensatório
	 * (o histórico nunca é editado) e o novo tipo é aplicado à candidatura indicada.
	 */
	public void applyEmailCorrection(long emailMessageId, @Nullable ResponseType type, @Nullable Long applicationId) {
		Optional<AutoMove> previous = this.jdbc.sql("""
				select application_id, from_status, to_status from application_event
				where email_message_id = :email and to_status is not null
				order by id desc limit 1
				""")
			.param("email", emailMessageId)
			.query((rs, row) -> new AutoMove(rs.getLong("application_id"),
					ApplicationStatus.valueOf(rs.getString("from_status")),
					ApplicationStatus.valueOf(rs.getString("to_status"))))
			.optional();
		ApplicationStatus newTarget = (type != null) ? type.targetStatus().orElse(null) : null;
		Instant now = this.clock.instant();
		if (previous.isPresent()) {
			AutoMove move = previous.get();
			Application moved = require(move.applicationId());
			boolean sameTarget = Objects.equals(applicationId, move.applicationId()) && newTarget == move.to();
			if (!sameTarget && moved.getStatus() == move.to()) {
				ApplicationStatus back = Objects.equals(applicationId, move.applicationId()) && newTarget != null ? newTarget
						: move.from();
				ApplicationStatus from = moved.getStatus();
				this.history.save(moved.moveTo(back, null, "Correção da classificação de um e-mail", EventOrigin.MANUAL,
						this.followUp, now));
				publishChange(moved, from, now);
			}
			if (sameTarget || Objects.equals(applicationId, move.applicationId())) {
				return;
			}
		}
		if (type != null && applicationId != null && !alreadyRecorded(emailMessageId, applicationId)) {
			recordResponse(require(applicationId), type, ResponseChannel.EMAIL, "Retorno confirmado na revisão", newTarget,
					EventOrigin.EMAIL, emailMessageId);
		}
	}

	private void recordResponse(Application application, ResponseType type, ResponseChannel channel, String note,
			ApplicationStatus target, EventOrigin origin, Long emailMessageId) {
		ApplicationStatus from = application.getStatus();
		Instant now = this.clock.instant();
		this.history.save(application.recordResponse(type, channel, note, target, origin, emailMessageId, now));
		captureSnapshotIfApplied(application, null, now);
		if (application.getStatus() != from) {
			publishChange(application, from, now);
		}
	}

	private boolean alreadyRecorded(long emailMessageId, long applicationId) {
		return this.jdbc.sql("select count(*) from application_event where email_message_id = :email and application_id = :app")
			.param("email", emailMessageId)
			.param("app", applicationId)
			.query(Long.class)
			.single() > 0;
	}

	private void publishChange(Application application, ApplicationStatus from, Instant now) {
		this.events.publishEvent(new ApplicationStatusChanged(application.getId(), application.getJobId(), from,
				application.getStatus(), EventOrigin.EMAIL, now));
	}

	private record AutoMove(long applicationId, ApplicationStatus from, ApplicationStatus to) {
	}

	public void changeResume(long applicationId, long resumeVersionId) {
		Application application = require(applicationId);
		this.resumes.requireSelectable(resumeVersionId);
		application.changeResume(resumeVersionId, this.clock.instant());
	}

	public void updateDetails(long applicationId, ApplicationCommands.UpdateDetails command) {
		require(applicationId).updateDetails(command.notes(), command.nextActionAt(), command.nextActionKind(),
				this.clock.instant());
	}

	/**
	 * Confirmação de candidatura lida de um e-mail: completa a data da aplicação.
	 * @return falso quando a candidatura ainda está em Interesse e precisa da sua revisão para ir a Aplicada
	 */
	public boolean confirmFromEmail(long applicationId, Instant receivedAt) {
		return require(applicationId).confirmApplied(receivedAt, this.clock.instant());
	}

	/**
	 * Registra a candidatura a partir do e-mail de confirmação de uma vaga que não estava no agente: cadastra a vaga
	 * (ou usa a já cadastrada), cria a candidatura em Aplicada na data do e-mail e liga o e-mail a ela.
	 */
	public long registerFromEmail(long emailId, ApplicationCommands.FromEmail command) {
		Mailbox.ClassifiedEmail email = this.mailbox.find(emailId).orElseThrow(() -> NotFoundException.of("E-mail", emailId));
		if (email.applicationId() != null && this.applications.existsById(email.applicationId())) {
			throw new ConflictException("Esse e-mail já está ligado a uma candidatura.", email.applicationId());
		}
		String title = firstNonBlank(command.title(), email.jobGuess(), email.subject());
		String company = firstNonBlank(command.company(), email.companyGuess(), null);
		if (title == null || company == null) {
			throw new BusinessRuleException("Informe o cargo e a empresa da vaga.");
		}
		long jobId;
		try {
			jobId = this.jobs.registerManual(new ManualJobCommand(title, company, null, null, null, null,
					blankToNull(command.url()), null)).id();
		}
		catch (ConflictException ex) {
			jobId = ex.existingId();
		}
		long applicationId = start(jobId, new ApplicationCommands.Start(ApplicationStatus.APLICADA,
				command.resumeVersionId(), command.channel(), email.receivedAt(),
				"Registrada pelo e-mail de confirmação"));
		this.mailbox.markHandled(emailId, applicationId);
		return applicationId;
	}

	private static String firstNonBlank(String first, String second, String third) {
		for (String value : new String[] { first, second, third }) {
			if (value != null && !value.isBlank()) {
				return value.strip();
			}
		}
		return null;
	}

	/**
	 * Agenda a próxima ação da candidatura como uma ação sua (um retorno da empresa não a cancela). Usado, por exemplo,
	 * para o próximo contato depois de uma entrevista.
	 */
	public void scheduleNextAction(long applicationId, Instant at, NextActionKind kind) {
		require(applicationId).scheduleNextAction(at, kind, this.clock.instant());
	}

	/** Você mandou um follow-up (por e-mail, LinkedIn ou WhatsApp): conta o envio e agenda o próximo. */
	public void registerFollowUp(long applicationId) {
		require(applicationId).registerFollowUp(this.followUp, this.clock.instant());
	}

	public void saveSnapshot(long applicationId, ApplicationCommands.SaveSnapshot command) {
		require(applicationId);
		String url = blankToNull(command.url());
		String title = command.title().strip();
		String company = command.company().strip();
		String description = blankToNull(command.descriptionMd());
		this.snapshots.findByApplicationId(applicationId)
			.ifPresentOrElse(snapshot -> snapshot.edit(url, title, company, description),
					() -> this.snapshots.save(JobSnapshot.manual(applicationId, url, title, company, description,
							this.clock.instant())));
	}

	public void attachPagePdf(long applicationId, byte[] content) {
		JobSnapshot snapshot = requireSnapshot(applicationId);
		if (content.length > MAX_PDF_BYTES || !isPdf(content)) {
			throw new BusinessRuleException("Envie a página da vaga em PDF, com até 10 MB.");
		}
		String previousKey = snapshot.getPagePdfKey();
		String key = "snapshots/" + applicationId + "-" + UUID.randomUUID() + ".pdf";
		this.storage.put(key, content, "application/pdf");
		snapshot.attachPagePdf(key);
		if (previousKey != null) {
			this.storage.delete(previousKey);
		}
	}

	@Transactional(readOnly = true)
	public DownloadLink pagePdfLink(long applicationId) {
		JobSnapshot snapshot = requireSnapshot(applicationId);
		if (snapshot.getPagePdfKey() == null) {
			throw new NotFoundException("Essa candidatura não tem o PDF da página salvo.");
		}
		String fileName = snapshot.getTitle() + ".pdf";
		return new DownloadLink(this.storage.presignedDownload(snapshot.getPagePdfKey(), fileName, PDF_LINK_TTL),
				this.clock.instant().plus(PDF_LINK_TTL));
	}

	// A cópia é tirada uma única vez, na primeira etapa que exige currículo; depois só muda por edição sua.
	private void captureSnapshotIfApplied(Application application, JobView knownJob, Instant now) {
		if (!application.getStatus().requiresResume() || this.snapshots.existsByApplicationId(application.getId())) {
			return;
		}
		JobView job = (knownJob != null) ? knownJob : this.jobs.get(application.getJobId());
		CaptureSource source = (job.origin() == JobOrigin.MANUAL) ? CaptureSource.MANUAL : CaptureSource.COLETA;
		this.snapshots.save(JobSnapshot.capture(application.getId(), job, source, now));
	}

	// A vaga encerrada também fecha no catálogo, o que entra no "tempo no ar" das métricas da empresa.
	private void closeJobIfEnded(Application application) {
		if (application.getStatus() == ApplicationStatus.ENCERRADA) {
			this.jobs.markClosed(application.getJobId());
		}
	}

	private Application require(long applicationId) {
		return this.applications.findById(applicationId)
			.orElseThrow(() -> NotFoundException.of("Candidatura", applicationId));
	}

	private JobSnapshot requireSnapshot(long applicationId) {
		require(applicationId);
		return this.snapshots.findByApplicationId(applicationId)
			.orElseThrow(() -> new NotFoundException("A candidatura " + applicationId + " ainda não tem a vaga salva."));
	}

	private static boolean isPdf(byte[] content) {
		return content.length > 4 && content[0] == '%' && content[1] == 'P' && content[2] == 'D' && content[3] == 'F';
	}

	private static String blankToNull(String value) {
		return (value == null || value.isBlank()) ? null : value.strip();
	}

}
