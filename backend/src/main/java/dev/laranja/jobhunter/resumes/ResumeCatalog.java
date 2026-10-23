package dev.laranja.jobhunter.resumes;

import dev.laranja.jobhunter.resumes.internal.ResumeVersion;
import dev.laranja.jobhunter.resumes.internal.ResumeVersionRepository;
import dev.laranja.jobhunter.shared.BusinessRuleException;
import dev.laranja.jobhunter.shared.NotFoundException;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Consulta das versões do currículo para os outros módulos. */
@Service
@Transactional(readOnly = true)
public class ResumeCatalog {

	private final ResumeVersionRepository resumes;

	ResumeCatalog(ResumeVersionRepository resumes) {
		this.resumes = resumes;
	}

	/** Garante que a versão existe e pode ser registrada numa candidatura (não está arquivada). */
	public ResumeRef requireSelectable(long id) {
		ResumeVersion resume = this.resumes.findById(id).orElseThrow(() -> NotFoundException.of("Currículo", id));
		if (resume.isArchived()) {
			throw new BusinessRuleException("O currículo \"" + resume.getLabel() + "\" está arquivado.");
		}
		return new ResumeRef(resume.getId(), resume.getLabel());
	}

}
