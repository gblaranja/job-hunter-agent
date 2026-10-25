package dev.laranja.jobhunter.tailoring.internal;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import dev.laranja.jobhunter.analysis.ResumeFit.JobAnalysisView;
import dev.laranja.jobhunter.jobs.JobView;
import dev.laranja.jobhunter.resumes.ResumeCatalog.ResumeProfile;
import dev.laranja.jobhunter.shared.ai.AiProperties;
import dev.laranja.jobhunter.shared.ai.LlmClient;
import dev.laranja.jobhunter.shared.ai.LlmClient.Effort;
import dev.laranja.jobhunter.shared.ai.LlmClient.LlmRequest;
import dev.laranja.jobhunter.shared.ai.LlmException;
import org.jspecify.annotations.Nullable;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/**
 * Reescreve o currículo base para uma vaga com o modelo maior. O currículo vai no bloco de sistema (em cache), então
 * adaptar o mesmo currículo para várias vagas só paga a vaga de cada uma.
 */
@Component
public class ResumeTailor {

	static final String PROMPT_ID = "tailor-resume@v1";

	private static final String PROMPT = load("prompts/tailor-resume.v1.md");

	private final LlmClient llm;

	private final AiProperties ai;

	ResumeTailor(LlmClient llm, AiProperties ai) {
		this.llm = llm;
		this.ai = ai;
	}

	public boolean enabled() {
		return this.llm.enabled();
	}

	public String version() {
		return this.ai.models().tailoring() + "|" + PROMPT_ID;
	}

	public TailoredDraft rewrite(JobView job, JobAnalysisView analysis, ResumeProfile base) {
		String system = PROMPT + "\n\n" + base.text();
		String user = """
				Vaga: %s
				Empresa: %s
				Local: %s · Modelo de trabalho: %s

				Análise da vaga
				Senioridade: %s
				Obrigatórias: %s
				Desejáveis: %s
				Já atendidas pelo currículo: %s
				Lacunas: %s
				Evidências no currículo (destaque estes trechos; não invente outros): %s
				Score de aderência atual: %s
				Resumo: %s

				Descrição da vaga:
				%s
				""".formatted(job.title(), job.company(), orDash(job.location()), job.workModel(),
				orDash(analysis.seniority()), join(analysis.requiredSkills()), join(analysis.niceToHave()),
				join(analysis.matchedSkills()), join(analysis.gaps()), evidence(analysis),
				(analysis.matchScore() != null) ? analysis.matchScore() : "—", orDash(analysis.summary()),
				(job.description() == null || job.description().isBlank())
						? "(sem descrição; use o título e os requisitos)" : job.description());
		TailoredDraft draft = this.llm.structured(new LlmRequest("tailor-resume", this.ai.models().tailoring(), system,
				user, 20000, Effort.HIGH, true), TailoredDraft.class);
		if (draft.markdown() == null || draft.markdown().isBlank()) {
			throw new LlmException("A resposta não trouxe o currículo reescrito.");
		}
		return draft;
	}

	private static String join(List<String> values) {
		return values.isEmpty() ? "—" : String.join(", ", values);
	}

	private static String evidence(JobAnalysisView analysis) {
		return analysis.evidence().isEmpty() ? "—"
				: analysis.evidence()
					.stream()
					.map(item -> item.requirement() + ": \"" + item.quote() + "\"")
					.collect(java.util.stream.Collectors.joining("; "));
	}

	private static String orDash(@Nullable String value) {
		return (value == null || value.isBlank()) ? "—" : value;
	}

	private static String load(String path) {
		try {
			return new ClassPathResource(path).getContentAsString(StandardCharsets.UTF_8).strip();
		}
		catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
	}

}
