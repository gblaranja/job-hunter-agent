package dev.laranja.jobhunter.tracking.internal;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import dev.laranja.jobhunter.shared.ai.AiProperties;
import dev.laranja.jobhunter.shared.ai.LlmClient;
import dev.laranja.jobhunter.shared.ai.LlmClient.Effort;
import dev.laranja.jobhunter.shared.ai.LlmClient.LlmRequest;
import dev.laranja.jobhunter.shared.ai.LlmException;
import dev.laranja.jobhunter.shared.money.MoneyFormat;
import dev.laranja.jobhunter.tracking.NegotiationDraft;
import dev.laranja.jobhunter.tracking.OfferView;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/**
 * Rascunho da negociação de uma proposta com o modelo maior. A resposta é descartada, em favor do texto fixo, se citar
 * um valor que não está entre os números informados.
 */
@Component
public class NegotiationDrafter {

	static final String PROMPT_ID = "draft-negotiation@v1";

	private static final String PROMPT = load("prompts/draft-negotiation.v1.md");

	/** Valores escritos com separador de milhar ("18.000", "18 000") ou sem ("18000"), com centavos opcionais. */
	private static final Pattern NUMBER = Pattern.compile("\\d{1,3}(?:[.\\s]\\d{3})+(?:,\\d{1,2})?|\\d+(?:,\\d{1,2})?");

	private static final Logger log = LoggerFactory.getLogger(NegotiationDrafter.class);

	private final LlmClient llm;

	private final AiProperties ai;

	NegotiationDrafter(LlmClient llm, AiProperties ai) {
		this.llm = llm;
		this.ai = ai;
	}

	public NegotiationDraft draft(OfferView offer, List<OfferView> others, NegotiationDraft.Request request) {
		if (this.llm.enabled()) {
			try {
				Output output = this.llm.structured(new LlmRequest("draft-negotiation", this.ai.models().interviews(),
						PROMPT, userMessage(offer, others, request), 2000, Effort.MEDIUM), Output.class);
				if (output.message() != null && !output.message().isBlank()) {
					if (onlyKnownNumbers(output.message(), offer, others, request)) {
						return new NegotiationDraft(output.message().strip(), "IA");
					}
					log.warn("Rascunho de negociação da IA descartado: citou um valor que não foi informado");
				}
			}
			catch (LlmException ex) {
				log.warn("Rascunho de negociação pelo modelo fixo: {}", ex.getMessage());
			}
		}
		return new NegotiationDraft(template(offer, request), "MODELO");
	}

	static String userMessage(OfferView offer, List<OfferView> others, NegotiationDraft.Request request) {
		StringBuilder text = new StringBuilder();
		text.append("Vaga: ").append(offer.title()).append(" · ").append(offer.company()).append('\n');
		text.append("Proposta: ").append(describe(offer)).append('\n');
		text.append("Valor desejado: ")
			.append((request.targetSalary() != null)
					? MoneyFormat.amount(request.targetSalary(), offer.currency(), offer.period()) : "não informado")
			.append('\n');
		text.append("Argumentos da pessoa: ")
			.append((request.arguments() != null && !request.arguments().isBlank()) ? request.arguments().strip()
					: "nenhum")
			.append('\n');
		text.append("Outras propostas em aberto:");
		if (others.isEmpty()) {
			text.append(" nenhuma");
		}
		others.forEach(other -> text.append("\n- ").append(describe(other)));
		return text.toString();
	}

	private static String describe(OfferView offer) {
		List<String> parts = new ArrayList<>();
		parts.add(offer.regime().name() + ", " + MoneyFormat.amount(offer.baseSalary(), offer.currency(), offer.period()));
		if (offer.annualBonus() != null) {
			parts.add("bônus de " + MoneyFormat.amount(offer.annualBonus(), offer.currency()) + " por ano");
		}
		if (offer.monthlyBenefits() != null) {
			parts.add("benefícios de " + MoneyFormat.amount(offer.monthlyBenefits(), "BRL") + " por mês");
		}
		if (offer.benefitsNotes() != null) {
			parts.add("benefícios: " + offer.benefitsNotes());
		}
		if (offer.equity() != null) {
			parts.add("equity: " + offer.equity());
		}
		return String.join("; ", parts);
	}

	static String template(OfferView offer, NegotiationDraft.Request request) {
		String current = MoneyFormat.amount(offer.baseSalary(), offer.currency(), offer.period());
		String ask = (request.targetSalary() != null)
				? "Para aceitar com tranquilidade, eu precisaria de um valor-base de "
						+ MoneyFormat.amount(request.targetSalary(), offer.currency(), offer.period()) + "."
				: "Gostaria de saber se há espaço para revisar o valor-base.";
		String arguments = (request.arguments() != null && !request.arguments().isBlank())
				? " " + request.arguments().strip() : "";
		return """
				Olá! Muito obrigado pela proposta para a posição de %s na %s. Gostei muito das conversas e tenho \
				interesse real em fazer parte do time.

				Analisando o pacote, o valor-base de %s ficou abaixo do que eu esperava. %s%s

				Há flexibilidade nesse ponto? Fico à disposição para conversarmos.

				[seu nome]""".formatted(offer.title(), offer.company(), current, ask, arguments);
	}

	/** Todo valor de três dígitos ou mais no texto precisa ser um dos números informados. */
	static boolean onlyKnownNumbers(String text, OfferView offer, List<OfferView> others,
			NegotiationDraft.Request request) {
		Set<String> known = new HashSet<>();
		add(known, request.targetSalary());
		for (OfferView each : concat(offer, others)) {
			add(known, each.baseSalary());
			add(known, each.annualBonus());
			add(known, each.monthlyBenefits());
		}
		if (request.arguments() != null) {
			numbers(request.arguments()).forEach(known::add);
		}
		return numbers(text).stream().allMatch(known::contains);
	}

	private static List<OfferView> concat(OfferView offer, List<OfferView> others) {
		List<OfferView> all = new ArrayList<>(others);
		all.add(offer);
		return all;
	}

	private static void add(Set<String> known, @Nullable BigDecimal value) {
		if (value != null) {
			known.add(value.setScale(0, RoundingMode.DOWN).toPlainString());
		}
	}

	private static List<String> numbers(String text) {
		List<String> found = new ArrayList<>();
		Matcher matcher = NUMBER.matcher(text);
		while (matcher.find()) {
			String integer = matcher.group().split(",")[0].replaceAll("[.\\s]", "");
			if (integer.length() >= 3) {
				found.add(integer.replaceFirst("^0+(?=\\d)", ""));
			}
		}
		return found;
	}

	private static String load(String path) {
		try {
			return new ClassPathResource(path).getContentAsString(StandardCharsets.UTF_8).strip();
		}
		catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
	}

	record Output(@JsonPropertyDescription("A mensagem pronta para copiar") @Nullable String message) {
	}

}
