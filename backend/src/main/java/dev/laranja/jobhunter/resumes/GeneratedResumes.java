package dev.laranja.jobhunter.resumes;

import dev.laranja.jobhunter.resumes.internal.ResumeService;
import org.jspecify.annotations.Nullable;

import org.springframework.stereotype.Service;

/**
 * Registro de currículos gerados pelo sistema (as versões adaptadas para uma vaga). Passam pela mesma validação,
 * storage e extração de texto e skills de um upload, e aparecem na tela Currículos como qualquer versão.
 */
@Service
public class GeneratedResumes {

	private final ResumeService service;

	GeneratedResumes(ResumeService service) {
		this.service = service;
	}

	public ResumeRef register(byte[] content, String fileName, String label, @Nullable String targetRole) {
		long id = this.service.upload(content, fileName, label, targetRole);
		return new ResumeRef(id, label);
	}

}
