package dev.laranja.jobhunter.interviews;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import org.jspecify.annotations.Nullable;

/**
 * A preparação para uma etapa: o que devem perguntar e como responder, o que revisar, o que saber da empresa, o que
 * perguntar e o que esclarecer (salário, regime, modelo de trabalho).
 */
@Schema(name = "InterviewPrepContent")
public record InterviewPrepContent(List<LikelyQuestion> questions, List<String> technicalTopics,
		List<String> companyFacts, List<String> questionsToAsk, List<String> pointsToClarify) {

	public InterviewPrepContent {
		questions = (questions != null) ? List.copyOf(questions) : List.of();
		technicalTopics = (technicalTopics != null) ? List.copyOf(technicalTopics) : List.of();
		companyFacts = (companyFacts != null) ? List.copyOf(companyFacts) : List.of();
		questionsToAsk = (questionsToAsk != null) ? List.copyOf(questionsToAsk) : List.of();
		pointsToClarify = (pointsToClarify != null) ? List.copyOf(pointsToClarify) : List.of();
	}

	/**
	 * Pergunta provável.
	 *
	 * @param reason por que ela deve aparecer (requisito, lacuna, eliminatório, etapa anterior)
	 * @param storyId história do seu banco que responde a pergunta; nula quando nenhuma serve
	 * @param answerOutline roteiro da resposta, só com o que está no currículo e nas histórias
	 */
	@Schema(name = "InterviewPrepQuestion")
	public record LikelyQuestion(String question, String reason, @Nullable Long storyId, @Nullable String storyTitle,
			String answerOutline) {
	}

}
