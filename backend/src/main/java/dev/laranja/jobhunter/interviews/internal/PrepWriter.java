package dev.laranja.jobhunter.interviews.internal;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import dev.laranja.jobhunter.interviews.InterviewPrepContent;
import dev.laranja.jobhunter.interviews.InterviewPrepContent.LikelyQuestion;
import dev.laranja.jobhunter.shared.ai.AiProperties;
import dev.laranja.jobhunter.shared.ai.LlmClient;
import dev.laranja.jobhunter.shared.ai.LlmClient.Effort;
import dev.laranja.jobhunter.shared.ai.LlmClient.LlmRequest;
import dev.laranja.jobhunter.shared.ai.LlmException;

import org.springframework.stereotype.Component;

/** Preparação com o modelo das entrevistas; a história sugerida só vale se o id existir no seu banco. */
@Component
class PrepWriter {

	static final String PROMPT_ID = "prepare-interview@v1";

	private static final String PROMPT = Prompts.load("prompts/prepare-interview.v1.md");

	private final LlmClient llm;

	private final AiProperties ai;

	PrepWriter(LlmClient llm, AiProperties ai) {
		this.llm = llm;
		this.ai = ai;
	}

	boolean enabled() {
		return this.llm.enabled();
	}

	String version() {
		return this.ai.models().interviews() + "|" + PROMPT_ID;
	}

	InterviewPrepContent write(PrepInputs in) {
		Draft draft = this.llm.structured(new LlmRequest("prepare-interview", this.ai.models().interviews(), PROMPT,
				userMessage(in), 12000, Effort.HIGH, true), Draft.class);
		if (draft.questions() == null || draft.questions().isEmpty()) {
			throw new LlmException("A resposta não trouxe as perguntas.");
		}
		Map<Long, PrepInputs.Story> stories = in.stories().stream()
			.collect(Collectors.toMap(PrepInputs.Story::id, Function.identity()));
		List<LikelyQuestion> questions = draft.questions().stream().map(q -> {
			PrepInputs.Story story = stories.get(q.storyId());
			return new LikelyQuestion(q.question(), q.reason(), (story != null) ? story.id() : null,
					(story != null) ? story.title() : null, q.answerOutline());
		}).toList();
		return new InterviewPrepContent(questions, draft.technicalTopics(), draft.companyFacts(),
				draft.questionsToAsk(), draft.pointsToClarify());
	}

	static String userMessage(PrepInputs in) {
		StringBuilder text = new StringBuilder();
		text.append("Etapa: ").append(in.stage() == dev.laranja.jobhunter.tracking.ApplicationStatus.TESTE_TECNICO
				? "teste técnico" : "entrevista").append('\n');
		text.append("Vaga: ").append(in.title()).append(" · ").append(in.company()).append('\n');
		text.append("Local: ").append(orDash(in.location())).append(" · Modelo: ").append(in.workModel())
			.append(" · Salário: ").append(orDash(in.salaryRange())).append(" · Regime: ").append(orDash(in.contractType()))
			.append('\n');
		text.append("Senioridade: ").append(orDash(in.seniority())).append('\n');
		text.append("Obrigatórias: ").append(join(in.requiredSkills())).append('\n');
		text.append("Desejáveis: ").append(join(in.niceToHave())).append('\n');
		text.append("Atendidas pelo currículo: ").append(join(in.matchedSkills())).append('\n');
		text.append("Lacunas: ").append(join(in.gaps())).append('\n');
		text.append("Resumo: ").append(orDash(in.summary())).append('\n');
		text.append("\nEliminatórios:");
		if (in.knockouts().isEmpty()) {
			text.append(" nenhum");
		}
		in.knockouts().forEach(k -> text.append("\n- ").append(k.type()).append(": ").append(k.text()).append(" → ")
			.append(orDash(k.status())));
		text.append("\nEvidências no currículo:");
		if (in.evidence().isEmpty()) {
			text.append(" nenhuma");
		}
		in.evidence().forEach(e -> text.append("\n- ").append(e.requirement()).append(": \"").append(e.quote()).append('"'));
		PrepInputs.Company c = in.companyFacts();
		text.append("\n\nEmpresa: tipo ").append(orDash(c.kind())).append(", setor ").append(orDash(c.sector()))
			.append(", porte ").append(orDash(c.sizeRange())).append(", sede ").append(orDash(c.hqLocation()))
			.append(", site ").append(orDash(c.website())).append(", GPTW ").append(orDash(c.gptw())).append('\n');
		text.append("\nHistórias STAR:");
		if (in.stories().isEmpty()) {
			text.append(" nenhuma");
		}
		in.stories().forEach(s -> text.append("\n- id ").append(s.id()).append(": ").append(s.title()).append(" [")
			.append(String.join(", ", s.skills())).append("] Situação: ").append(s.situation()).append(" Ação: ")
			.append(s.action()).append(" Resultado: ").append(s.result()));
		text.append("\n\nEtapas anteriores:");
		if (in.debriefs().isEmpty()) {
			text.append(" nenhuma");
		}
		in.debriefs().forEach(d -> text.append("\n- ").append(d.interviewedOn()).append(" (").append(d.stage())
			.append("): perguntaram ").append(join(d.questions())).append("; faltou: ").append(orDash(d.toImprove()))
			.append("; próximos passos: ").append(orDash(d.nextSteps())));
		text.append("\n\nPerguntas recorrentes nas entrevistas da pessoa: ")
			.append(join(in.recurringQuestions().stream().map(q -> q.question()).toList()));
		text.append("\n\nDescrição da vaga:\n").append(orDash(in.description()));
		return text.toString();
	}

	private static String join(List<String> values) {
		return values.isEmpty() ? "—" : String.join(", ", values);
	}

	private static String orDash(String value) {
		return (value == null || value.isBlank()) ? "—" : value;
	}

	record Draft(@JsonPropertyDescription("De 8 a 12 perguntas prováveis") List<DraftQuestion> questions,
			@JsonPropertyDescription("Tópicos técnicos para revisar") List<String> technicalTopics,
			@JsonPropertyDescription("Fatos úteis da empresa e da vaga") List<String> companyFacts,
			@JsonPropertyDescription("Perguntas para a pessoa fazer") List<String> questionsToAsk,
			@JsonPropertyDescription("O que confirmar: salário, regime, modelo de trabalho") List<String> pointsToClarify) {
	}

	record DraftQuestion(@JsonPropertyDescription("A pergunta") String question,
			@JsonPropertyDescription("O que motiva a pergunta") String reason,
			@JsonPropertyDescription("Id da história sugerida; 0 se nenhuma serve") long storyId,
			@JsonPropertyDescription("Roteiro da resposta em 2 a 4 frases") String answerOutline) {
	}

}
