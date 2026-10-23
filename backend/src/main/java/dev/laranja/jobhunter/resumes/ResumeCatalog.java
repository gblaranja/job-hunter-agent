package dev.laranja.jobhunter.resumes;

import java.util.List;
import java.util.Optional;

import dev.laranja.jobhunter.resumes.internal.ResumeVersion;
import dev.laranja.jobhunter.resumes.internal.ResumeVersionRepository;
import dev.laranja.jobhunter.shared.BusinessRuleException;
import dev.laranja.jobhunter.shared.NotFoundException;
import org.jspecify.annotations.Nullable;

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

	/** O currículo padrão, com o texto extraído e as skills, base do score de aderência. */
	public Optional<ResumeProfile> defaultResume() {
		return this.resumes.findByDefaultVersionTrue().map(ResumeCatalog::toProfile);
	}

	public Optional<ResumeProfile> find(long id) {
		return this.resumes.findById(id).map(ResumeCatalog::toProfile);
	}

	@Transactional
	public void updateSkills(long id, List<String> skills) {
		this.resumes.findById(id)
			.orElseThrow(() -> NotFoundException.of("Currículo", id))
			.useSkills(skills);
	}

	private static ResumeProfile toProfile(ResumeVersion resume) {
		return new ResumeProfile(resume.getId(), resume.getLabel(), resume.getTargetRole(), resume.getExtractedText(),
				resume.getSkills(), resume.isDefaultVersion());
	}

	/**
	 * @param text texto extraído do arquivo; nulo quando não houver texto legível
	 * @param skills skills normalizadas; vazio até a análise do currículo rodar
	 */
	public record ResumeProfile(long id, String label, @Nullable String targetRole, @Nullable String text,
			List<String> skills, boolean isDefault) {
	}

}
