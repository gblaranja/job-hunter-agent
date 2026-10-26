package dev.laranja.jobhunter.inbox.internal;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import dev.laranja.jobhunter.inbox.EmailClassification;
import dev.laranja.jobhunter.shared.TextNormalizer;
import dev.laranja.jobhunter.shared.ai.AiProperties;
import dev.laranja.jobhunter.shared.ai.LlmClient;
import dev.laranja.jobhunter.shared.ai.LlmClient.Effort;
import dev.laranja.jobhunter.shared.ai.LlmClient.LlmRequest;
import dev.laranja.jobhunter.shared.ai.LlmException;
import org.jspecify.annotations.Nullable;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/**
 * Classifica os e-mails que não são alertas. Com o LLM, usa as suas correções recentes como exemplos; sem ele (ou se
 * falhar), usa palavras-chave com confiança baixa, para tudo passar pela sua revisão.
 */
@Component
public class ResponseClassifier {

	/** Confiança das regras: abaixo do limite de 0,7, nada muda de status sem você confirmar. */
	static final double RULE_CONFIDENCE = 0.6;

	private static final List<Rule> RULES = List.of(
			new Rule(EmailClassification.RECUSA,
					"infelizmente|nao seguiremos|nao vamos seguir|outro candidato|outra pessoa candidata|nao foi selecionad|processo foi encerrado|optamos por seguir|not moving forward|unfortunately"),
			new Rule(EmailClassification.OFERTA, "proposta de trabalho|carta oferta|oferta de emprego|job offer|offer letter|gostariamos de fazer uma proposta"),
			new Rule(EmailClassification.CONFIRMACAO_CANDIDATURA,
					"recebemos sua candidatura|recebemos a sua candidatura|candidatura recebida|candidatura foi recebida|obrigad[oa] por se candidatar|agradecemos (sua|a sua|seu) (candidatura|interesse)|application received|received your application|thank you for applying|thanks for applying"),
			new Rule(EmailClassification.TESTE_TECNICO, "teste tecnico|desafio tecnico|case tecnico|take home|coding challenge|avaliacao tecnica"),
			new Rule(EmailClassification.CONVITE_ENTREVISTA, "entrevista|bate papo|agendar uma conversa|interview|disponibilidade para conversar"),
			new Rule(EmailClassification.PEDIDO_INFORMACAO, "pretensao salarial|enviar seus documentos|poderia nos informar|disponibilidade de inicio|qual sua disponibilidade"),
			new Rule(EmailClassification.CONTATO_RECRUTADOR,
					"vi seu perfil|vi o seu perfil|encontrei seu perfil|seu perfil (no|do) linkedin|tenho uma oportunidade|temos uma oportunidade|oportunidade (que|para voce)|gostaria de apresentar|came across your profile|an opportunity|i am a recruiter|sou recrutador|headhunter"));

	private final LlmClient llm;

	private final AiProperties ai;

	private final String prompt;

	ResponseClassifier(LlmClient llm, AiProperties ai) {
		this.llm = llm;
		this.ai = ai;
		try {
			this.prompt = new ClassPathResource("prompts/classify-email.v2.md").getContentAsString(StandardCharsets.UTF_8).strip();
		}
		catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
	}

	public Result classify(String sender, @Nullable String subject, String body, List<Correction> corrections) {
		if (this.llm.enabled()) {
			try {
				return withLlm(sender, subject, body, corrections);
			}
			catch (LlmException ex) {
				Result fallback = byRules(subject, body);
				return new Result(fallback.classification(), fallback.confidence(), null, null,
						"Regras (o LLM falhou: " + ex.getMessage() + ")");
			}
		}
		return byRules(subject, body);
	}

	Result byRules(@Nullable String subject, String body) {
		String text = " " + TextNormalizer.normalize((subject == null ? "" : subject) + " " + body) + " ";
		for (Rule rule : RULES) {
			if (rule.pattern().matcher(text).find()) {
				return new Result(rule.classification(), RULE_CONFIDENCE, null, null, "Palavras-chave de " + rule.classification());
			}
		}
		return new Result(EmailClassification.OUTRO, 0.3, null, null, "Nenhuma palavra-chave reconhecida");
	}

	private Result withLlm(String sender, @Nullable String subject, String body, List<Correction> corrections) {
		String examples = corrections.isEmpty() ? "" : "\n\nCorreções recentes da pessoa (siga o mesmo critério):\n"
				+ corrections.stream()
					.map(c -> "- \"" + c.subject() + "\" de " + c.sender() + ": " + c.classification())
					.collect(Collectors.joining("\n"));
		String user = "Remetente: " + sender + "\nAssunto: " + (subject == null ? "" : subject) + "\n\n" + body;
		LlmResult result = this.llm.structured(new LlmRequest("classify-email", this.ai.models().classification(),
				this.prompt + examples, user, 1000, Effort.LOW), LlmResult.class);
		EmailClassification classification;
		try {
			classification = EmailClassification.valueOf(result.classification());
		}
		catch (IllegalArgumentException | NullPointerException ex) {
			classification = EmailClassification.OUTRO;
		}
		double confidence = Math.max(0, Math.min(1, result.confidence()));
		return new Result(classification, confidence, blankToNull(result.company()), blankToNull(result.jobTitle()),
				result.rationale(), blankToNull(result.recruiterName()), blankToNull(result.agency()));
	}

	private static @Nullable String blankToNull(@Nullable String value) {
		return (value == null || value.isBlank()) ? null : value.strip();
	}

	/**
	 * @param recruiterName quem escreveu (abordagens de recrutador)
	 * @param agency agência ou consultoria de recrutamento, quando diferente da empresa cliente
	 */
	public record Result(EmailClassification classification, double confidence, @Nullable String company,
			@Nullable String jobTitle, @Nullable String rationale, @Nullable String recruiterName,
			@Nullable String agency) {

		public Result(EmailClassification classification, double confidence, @Nullable String company,
				@Nullable String jobTitle, @Nullable String rationale) {
			this(classification, confidence, company, jobTitle, rationale, null, null);
		}

	}

	public record Correction(String sender, String subject, EmailClassification classification) {
	}

	record LlmResult(
			@JsonPropertyDescription("ALERTA_VAGA, CONVITE_ENTREVISTA, TESTE_TECNICO, PEDIDO_INFORMACAO, OFERTA, RECUSA, CONFIRMACAO_CANDIDATURA, CONTATO_RECRUTADOR, OUTRO ou IRRELEVANTE") String classification,
			@JsonPropertyDescription("Empresa contratante citada; vazio se não houver") String company,
			@JsonPropertyDescription("Cargo citado; vazio se não houver") String jobTitle,
			@JsonPropertyDescription("Confiança de 0 a 1") double confidence,
			@JsonPropertyDescription("Uma frase explicando a classificação") String rationale,
			@JsonPropertyDescription("Nome de quem escreveu; vazio se não houver") String recruiterName,
			@JsonPropertyDescription("Agência ou consultoria de recrutamento, se diferente da empresa; vazio se não houver") String agency) {
	}

	private record Rule(EmailClassification classification, Pattern pattern) {

		Rule(EmailClassification classification, String words) {
			this(classification, Pattern.compile(" (" + words + ")"));
		}

	}

}
