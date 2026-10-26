package dev.laranja.jobhunter.network.internal;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import dev.laranja.jobhunter.analysis.ResumeFit.JobAnalysisView;
import dev.laranja.jobhunter.jobs.JobScope;
import dev.laranja.jobhunter.jobs.JobView;
import dev.laranja.jobhunter.network.ContactView;
import dev.laranja.jobhunter.network.DraftKind;
import dev.laranja.jobhunter.network.MessageDraft;
import dev.laranja.jobhunter.shared.ai.AiProperties;
import dev.laranja.jobhunter.shared.ai.LlmClient;
import dev.laranja.jobhunter.shared.ai.LlmClient.Effort;
import dev.laranja.jobhunter.shared.ai.LlmClient.LlmRequest;
import dev.laranja.jobhunter.shared.ai.LlmException;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/**
 * Rascunha pedidos de indicação, follow-ups, agradecimentos e respostas a recrutadores. Com a Claude API, usa a vaga,
 * a análise, o contato e o currículo, com as mesmas regras de veracidade do currículo adaptado; sem ela (ou se
 * falhar), devolve um modelo de texto fixo para você completar.
 */
@Component
public class MessageDrafter {

	static final String PROMPT_ID = "draft-message@v1";

	private static final Logger log = LoggerFactory.getLogger(MessageDrafter.class);

	private static final String PROMPT = load("prompts/draft-message.v1.md");

	private static final Pattern ENGLISH = Pattern.compile("\\b(the|and|with|you|we|our|experience|requirements)\\b");

	private static final Pattern PORTUGUESE = Pattern.compile("\\b(de|com|para|você|vaga|experiência|requisitos|nós)\\b");

	private final LlmClient llm;

	private final AiProperties ai;

	MessageDrafter(LlmClient llm, AiProperties ai) {
		this.llm = llm;
		this.ai = ai;
	}

	public MessageDraft draft(DraftKind kind, JobView job, @Nullable JobAnalysisView analysis,
			@Nullable ContactView contact, @Nullable String resumeText) {
		String language = language(job);
		if (this.llm.enabled()) {
			try {
				return withLlm(kind, job, analysis, contact, resumeText, language);
			}
			catch (LlmException ex) {
				log.warn("Rascunho da vaga {} pelo modelo fixo: {}", job.id(), ex.getMessage());
			}
		}
		return Templates.draft(kind, job, analysis, contact, language);
	}

	private MessageDraft withLlm(DraftKind kind, JobView job, @Nullable JobAnalysisView analysis,
			@Nullable ContactView contact, @Nullable String resumeText, String language) {
		String resume = (resumeText == null || resumeText.isBlank())
				? "(sem currículo legível cadastrado: não cite competências nem experiências)" : truncate(resumeText, 12000);
		String user = """
				Tipo: %s
				Idioma: %s

				Vaga: %s
				Empresa: %s
				Competências da vaga que o currículo comprova: %s
				Resumo da vaga: %s

				Contato: %s

				Descrição da vaga:
				%s
				""".formatted(kind, language, job.title(), job.company(),
				(analysis == null || analysis.matchedSkills().isEmpty()) ? "—" : String.join(", ", analysis.matchedSkills()),
				(analysis == null || analysis.summary() == null) ? "—" : analysis.summary(), describe(contact),
				(job.description() == null) ? "(sem descrição)" : truncate(job.description(), 6000));
		LlmDraft result = this.llm.structured(new LlmRequest("draft-message", this.ai.models().drafting(),
				PROMPT + "\n\nCurrículo da pessoa:\n" + resume, user, 2000, Effort.LOW),
				LlmDraft.class);
		if (result.text() == null || result.text().isBlank()) {
			throw new LlmException("A resposta não trouxe o texto da mensagem.");
		}
		String note = (kind == DraftKind.PEDIDO_INDICACAO) ? limitNote(result.linkedinNote()) : null;
		return new MessageDraft(kind, result.text().strip(), note, language, "IA");
	}

	/** Idioma da vaga: inglês nas internacionais ou quando a descrição está em inglês. */
	static String language(JobView job) {
		String text = (job.title() + " " + ((job.description() == null) ? "" : job.description())).toLowerCase(Locale.ROOT);
		long english = count(ENGLISH.matcher(text));
		long portuguese = count(PORTUGUESE.matcher(text));
		if (english == portuguese) {
			return (job.scope() == JobScope.INTERNACIONAL) ? "en" : "pt";
		}
		return (english > portuguese) ? "en" : "pt";
	}

	/** O convite do LinkedIn aceita no máximo 300 caracteres: corta na última palavra inteira. */
	static @Nullable String limitNote(@Nullable String note) {
		if (note == null || note.isBlank()) {
			return null;
		}
		String text = note.strip();
		if (text.length() <= MessageDraft.LINKEDIN_NOTE_LIMIT) {
			return text;
		}
		String cut = text.substring(0, MessageDraft.LINKEDIN_NOTE_LIMIT - 1);
		int space = cut.lastIndexOf(' ');
		return ((space > 200) ? cut.substring(0, space) : cut).strip() + "…";
	}

	private static String describe(@Nullable ContactView contact) {
		if (contact == null) {
			return "— (sem contato escolhido)";
		}
		return contact.name() + " · " + contact.role() + ((contact.position() != null) ? " · " + contact.position() : "");
	}

	private static long count(Matcher matcher) {
		long count = 0;
		while (matcher.find()) {
			count++;
		}
		return count;
	}

	private static String truncate(String value, int max) {
		return (value.length() <= max) ? value : value.substring(0, max);
	}

	private static String load(String path) {
		try {
			return new ClassPathResource(path).getContentAsString(StandardCharsets.UTF_8).strip();
		}
		catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
	}

	record LlmDraft(@JsonPropertyDescription("Mensagem pronta para copiar, terminando com [seu nome]") String text,
			@JsonPropertyDescription("Convite de conexão do LinkedIn com até 300 caracteres; vazio se o tipo não for PEDIDO_INDICACAO") String linkedinNote) {
	}

	/** Modelos fixos, para o produto funcionar sem a Claude API. */
	static final class Templates {

		private Templates() {
		}

		static MessageDraft draft(DraftKind kind, JobView job, @Nullable JobAnalysisView analysis,
				@Nullable ContactView contact, String language) {
			boolean en = "en".equals(language);
			String name = (contact != null) ? firstName(contact.name()) : null;
			String greeting = en ? ((name != null) ? "Hi " + name + "," : "Hi,") : ((name != null) ? "Olá, " + name + "!" : "Olá!");
			List<String> skills = (analysis == null) ? List.of() : analysis.matchedSkills().stream().limit(3).toList();
			String skillText = skills.isEmpty() ? (en ? "[your main skills]" : "[suas principais competências]")
					: String.join(", ", skills);
			String text = switch (kind) {
				case PEDIDO_INDICACAO -> en ? """
						%s

						I saw the %s opening at %s and I believe my background fits well: I work with %s. Would you be open to referring me, or telling me a bit about the team? I can send my resume and the job link.

						Thanks in advance!
						[your name]""".formatted(greeting, job.title(), job.company(), skillText) : """
						%s

						Vi a vaga de %s na %s e acredito que meu perfil combina: trabalho com %s. Você toparia me indicar ou me contar um pouco sobre o time? Posso mandar o currículo e o link da vaga.

						Obrigado(a) desde já!
						[seu nome]""".formatted(greeting, job.title(), job.company(), skillText);
				case FOLLOW_UP -> en ? """
						%s

						I applied for the %s position at %s and wanted to reaffirm my interest. Is there any update on the next steps of the process?

						Thank you!
						[your name]""".formatted(greeting, job.title(), job.company()) : """
						%s

						Me candidatei à vaga de %s na %s e queria reforçar meu interesse. Há alguma novidade sobre os próximos passos do processo?

						Obrigado(a)!
						[seu nome]""".formatted(greeting, job.title(), job.company());
				case AGRADECIMENTO -> en ? """
						%s

						Thank you for your time today in the interview for the %s position. I enjoyed talking about [topic from the conversation] and I'm even more interested in joining %s.

						Best regards,
						[your name]""".formatted(greeting, job.title(), job.company()) : """
						%s

						Obrigado(a) pelo tempo na entrevista de hoje para a vaga de %s. Gostei de conversar sobre [ponto da conversa] e saí ainda mais interessado(a) em fazer parte da %s.

						Um abraço,
						[seu nome]""".formatted(greeting, job.title(), job.company());
				case RESPOSTA_RECRUTADOR -> en ? """
						%s

						Thanks for reaching out about the %s opportunity at %s. I'm interested in learning more. Could you share the salary range, the work model and the type of contract? I'm available for a call [days and times].

						Best,
						[your name]""".formatted(greeting, job.title(), job.company()) : """
						%s

						Obrigado(a) pelo contato sobre a oportunidade de %s na %s. Tenho interesse em saber mais. Você pode me passar a faixa salarial, o modelo de trabalho e o regime de contratação? Tenho disponibilidade para conversar [dias e horários].

						Abraço,
						[seu nome]""".formatted(greeting, job.title(), job.company());
			};
			String note = null;
			if (kind == DraftKind.PEDIDO_INDICACAO) {
				note = limitNote(en ? "%s I'm applying for the %s role at %s and would love to connect and hear about the team."
					.formatted(greeting, job.title(), job.company())
						: "%s Estou me candidatando à vaga de %s na %s e gostaria de me conectar para saber mais sobre o time."
							.formatted(greeting, job.title(), job.company()));
			}
			return new MessageDraft(kind, text, note, language, "MODELO");
		}

		private static String firstName(String name) {
			String stripped = name.strip();
			int space = stripped.indexOf(' ');
			return (space > 0) ? stripped.substring(0, space) : stripped;
		}

	}

}
