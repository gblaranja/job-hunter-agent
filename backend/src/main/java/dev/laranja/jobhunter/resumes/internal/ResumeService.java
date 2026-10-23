package dev.laranja.jobhunter.resumes.internal;

import java.net.URI;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;

import dev.laranja.jobhunter.resumes.DefaultResumeChanged;
import dev.laranja.jobhunter.resumes.ResumeUploaded;
import dev.laranja.jobhunter.shared.BusinessRuleException;
import dev.laranja.jobhunter.shared.ConflictException;
import dev.laranja.jobhunter.shared.NotFoundException;
import dev.laranja.jobhunter.shared.storage.DownloadLink;
import dev.laranja.jobhunter.shared.storage.ObjectStorage;
import org.jspecify.annotations.Nullable;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Upload e manutenção das versões do currículo. */
@Service
@Transactional
public class ResumeService {

	static final Duration DOWNLOAD_TTL = Duration.ofMinutes(5);

	private final ResumeVersionRepository resumes;

	private final ResumeFileValidator validator;

	private final ResumeTextExtractor textExtractor;

	private final ObjectStorage storage;

	private final ApplicationEventPublisher events;

	private final Clock clock;

	ResumeService(ResumeVersionRepository resumes, ResumeFileValidator validator, ResumeTextExtractor textExtractor,
			ObjectStorage storage, ApplicationEventPublisher events, Clock clock) {
		this.resumes = resumes;
		this.validator = validator;
		this.textExtractor = textExtractor;
		this.storage = storage;
		this.events = events;
		this.clock = clock;
	}

	public long upload(byte[] content, String originalFileName, String label, String targetRole) {
		ResumeFileType type = this.validator.validate(content);
		String sha256 = sha256(content);
		this.resumes.findBySha256(sha256).ifPresent(existing -> {
			throw new ConflictException("Esse arquivo já foi enviado como \"" + existing.getLabel() + "\".",
					existing.getId());
		});
		String fileName = (originalFileName == null || originalFileName.isBlank())
				? "curriculo." + type.extension() : originalFileName.strip();
		String resolvedLabel = (label == null || label.isBlank()) ? stripExtension(fileName) : label.strip();
		boolean firstDefault = !this.resumes.existsByDefaultVersionTrue();
		// O nome no storage é sempre gerado aqui; o nome original só aparece no download.
		String fileKey = "resumes/" + UUID.randomUUID() + "." + type.extension();

		this.storage.put(fileKey, content, type.contentType());
		ResumeVersion saved;
		try {
			ResumeVersion resume = ResumeVersion.create(resolvedLabel, null, fileKey, fileName, type, content.length,
					sha256, firstDefault, this.clock.instant());
			resume.changeTargetRole(targetRole);
			resume.useExtractedText(this.textExtractor.extract(content));
			saved = this.resumes.saveAndFlush(resume);
		}
		catch (RuntimeException ex) {
			this.storage.delete(fileKey);
			throw ex;
		}
		this.events.publishEvent(new ResumeUploaded(saved.getId()));
		if (firstDefault) {
			this.events.publishEvent(new DefaultResumeChanged(saved.getId()));
		}
		return saved.getId();
	}

	public void update(long id, UpdateResumeCommand command) {
		ResumeVersion resume = require(id);
		if (command.label() != null) {
			resume.rename(command.label());
		}
		if (command.targetRole() != null) {
			resume.changeTargetRole(command.targetRole());
		}
		if (Boolean.FALSE.equals(command.archived())) {
			resume.unarchive();
		}
		if (Boolean.TRUE.equals(command.makeDefault()) && !resume.isDefaultVersion()) {
			// Limpa o padrão anterior antes: o índice único não aceita dois padrões nem por um instante.
			this.resumes.clearDefaultExcept(id);
			resume.markAsDefault();
			this.events.publishEvent(new DefaultResumeChanged(id));
		}
		else if (Boolean.FALSE.equals(command.makeDefault()) && resume.isDefaultVersion()) {
			throw new BusinessRuleException("Para trocar o currículo padrão, defina outro como padrão.");
		}
		if (Boolean.TRUE.equals(command.archived())) {
			resume.archive();
		}
	}

	@Transactional(readOnly = true)
	public DownloadLink downloadLink(long id) {
		ResumeVersion resume = require(id);
		Instant expiresAt = this.clock.instant().plus(DOWNLOAD_TTL);
		URI url = this.storage.presignedDownload(resume.getFileKey(), resume.getFileName(), DOWNLOAD_TTL);
		return new DownloadLink(url, expiresAt);
	}

	private ResumeVersion require(long id) {
		return this.resumes.findById(id).orElseThrow(() -> NotFoundException.of("Currículo", id));
	}

	private static String stripExtension(String fileName) {
		int dot = fileName.lastIndexOf('.');
		return (dot > 0) ? fileName.substring(0, dot) : fileName;
	}

	private static String sha256(byte[] content) {
		try {
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
		}
		catch (NoSuchAlgorithmException ex) {
			throw new IllegalStateException("SHA-256 indisponível na JVM", ex);
		}
	}

	/**
	 * Alteração parcial: campos nulos ficam como estão.
	 *
	 * @param makeDefault {@code true} torna esta versão o currículo padrão
	 */
	public record UpdateResumeCommand(@Nullable String label, @Nullable String targetRole,
			@Nullable Boolean makeDefault, @Nullable Boolean archived) {
	}

}
