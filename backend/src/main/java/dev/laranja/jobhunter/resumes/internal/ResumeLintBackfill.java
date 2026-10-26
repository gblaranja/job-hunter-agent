package dev.laranja.jobhunter.resumes.internal;

import dev.laranja.jobhunter.shared.storage.ObjectStorage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.json.JsonMapper;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Roda o teste "o que o ATS lê" nas versões enviadas antes dele existir. Idempotente. */
@Component
class ResumeLintBackfill implements ApplicationRunner {

	private static final Logger log = LoggerFactory.getLogger(ResumeLintBackfill.class);

	private final ResumeVersionRepository resumes;

	private final ObjectStorage storage;

	private final ResumeTextExtractor extractor;

	private final JsonMapper json;

	ResumeLintBackfill(ResumeVersionRepository resumes, ObjectStorage storage, ResumeTextExtractor extractor,
			JsonMapper json) {
		this.resumes = resumes;
		this.storage = storage;
		this.extractor = extractor;
		this.json = json;
	}

	@Override
	@Transactional
	public void run(ApplicationArguments args) {
		for (ResumeVersion resume : this.resumes.findByLintIsNull()) {
			try {
				ResumeTextExtractor.Extracted extracted = this.extractor.extractDocument(this.storage.get(resume.getFileKey()));
				resume.useLint(this.json.writeValueAsString(ResumeLint.check(extracted.text(), extracted.pages())));
			}
			catch (RuntimeException ex) {
				log.warn("Currículo {} sem o teste do ATS: {}", resume.getId(), ex.getMessage());
			}
		}
	}

}
