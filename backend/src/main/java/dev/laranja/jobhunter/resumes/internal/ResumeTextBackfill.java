package dev.laranja.jobhunter.resumes.internal;

import dev.laranja.jobhunter.resumes.ResumeUploaded;
import dev.laranja.jobhunter.shared.storage.ObjectStorage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Extrai o texto dos currículos enviados antes da extração existir e republica {@link ResumeUploaded} para a
 * análise extrair as skills deles. Idempotente.
 */
@Component
class ResumeTextBackfill implements ApplicationRunner {

	private static final Logger log = LoggerFactory.getLogger(ResumeTextBackfill.class);

	private final ResumeVersionRepository resumes;

	private final ObjectStorage storage;

	private final ResumeTextExtractor extractor;

	private final ApplicationEventPublisher events;

	ResumeTextBackfill(ResumeVersionRepository resumes, ObjectStorage storage, ResumeTextExtractor extractor,
			ApplicationEventPublisher events) {
		this.resumes = resumes;
		this.storage = storage;
		this.extractor = extractor;
		this.events = events;
	}

	@Override
	@Transactional
	public void run(ApplicationArguments args) {
		for (ResumeVersion resume : this.resumes.findByExtractedTextIsNull()) {
			try {
				String text = this.extractor.extract(this.storage.get(resume.getFileKey()));
				if (text != null) {
					resume.useExtractedText(text);
					this.events.publishEvent(new ResumeUploaded(resume.getId()));
				}
			}
			catch (RuntimeException ex) {
				log.warn("Currículo {} sem texto extraído: {}", resume.getId(), ex.getMessage());
			}
		}
	}

}
