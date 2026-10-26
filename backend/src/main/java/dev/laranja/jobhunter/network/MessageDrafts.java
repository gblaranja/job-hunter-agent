package dev.laranja.jobhunter.network;

import dev.laranja.jobhunter.analysis.ResumeFit;
import dev.laranja.jobhunter.analysis.ResumeFit.JobAnalysisView;
import dev.laranja.jobhunter.jobs.JobCatalog;
import dev.laranja.jobhunter.jobs.JobView;
import dev.laranja.jobhunter.network.internal.MessageDrafter;
import dev.laranja.jobhunter.resumes.ResumeCatalog;
import dev.laranja.jobhunter.resumes.ResumeCatalog.ResumeProfile;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import org.jspecify.annotations.Nullable;

import org.springframework.stereotype.Service;

/**
 * Rascunhos de mensagem para uma vaga. Nada é guardado nem enviado: você copia, ajusta e manda; ao marcar como
 * enviada, a conversa entra no histórico do contato (e o follow-up conta na candidatura).
 */
@Service
public class MessageDrafts {

	private final MessageDrafter drafter;

	private final JobCatalog jobs;

	private final ResumeFit fit;

	private final ResumeCatalog resumes;

	private final ContactBook contacts;

	MessageDrafts(MessageDrafter drafter, JobCatalog jobs, ResumeFit fit, ResumeCatalog resumes, ContactBook contacts) {
		this.drafter = drafter;
		this.jobs = jobs;
		this.fit = fit;
		this.resumes = resumes;
		this.contacts = contacts;
	}

	public MessageDraft draft(long jobId, DraftRequest request) {
		JobView job = this.jobs.get(jobId);
		ContactView contact = (request.contactId() != null) ? this.contacts.get(request.contactId()) : null;
		JobAnalysisView analysis = this.fit.analysis(jobId).orElse(null);
		String resume = this.resumes.defaultResume().map(ResumeProfile::text).orElse(null);
		return this.drafter.draft(request.kind(), job, analysis, contact, resume);
	}

	/** @param contactId para quem é a mensagem; sem contato, a saudação fica neutra */
	@Schema(name = "DraftRequest")
	public record DraftRequest(@NotNull DraftKind kind, @Nullable Long contactId) {
	}

}
