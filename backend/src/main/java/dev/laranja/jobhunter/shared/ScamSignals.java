package dev.laranja.jobhunter.shared;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jspecify.annotations.Nullable;

/**
 * Sinais de golpe num texto de vaga ou de mensagem de recrutador: pedido de pagamento, contato só por WhatsApp e
 * e-mail gratuito no lugar do domínio da empresa. Salário ausente ou "a combinar" não é sinal: a maioria das vagas
 * no Brasil não informa.
 */
public final class ScamSignals {

	private static final Pattern PAYMENT = Pattern.compile("""
			taxa de (inscricao|cadastro|adesao|treinamento|matricula|admissao)|pagar (uma |a )?taxa|\
			pagamento (antecipado|da taxa|de taxa|do curso|do kit|do material|do treinamento)|\
			(deposito|transferencia|pix) (antecipad|para garantir|para reservar|de r\\$)|investimento inicial|\
			(comprar|compra|adquirir) (o |do |de |um )?(kit|material|curso|uniforme)|curso (pago|obrigatorio pago)|\
			(application|registration|training) fee|pay (a |the )?fee|upfront payment""");

	private static final Pattern WHATSAPP = Pattern.compile("whats ?app|wa\\.me/|\\bzap\\b|\\bwpp\\b");

	private static final Pattern EMAIL = Pattern.compile("[a-z0-9._%+-]+@([a-z0-9-]+(\\.[a-z0-9-]+)+)");

	private static final Pattern URL = Pattern.compile("https?://([^/\\s]+)");

	private static final Set<String> FREE_EMAIL_DOMAINS = Set.of("gmail.com", "hotmail.com", "hotmail.com.br",
			"outlook.com", "outlook.com.br", "live.com", "yahoo.com", "yahoo.com.br", "bol.com.br", "uol.com.br",
			"icloud.com", "protonmail.com", "proton.me", "terra.com.br", "ig.com.br");

	private ScamSignals() {
	}

	/** Sinais encontrados no texto, na ordem de gravidade. */
	public static List<Signal> detect(@Nullable String text) {
		if (text == null || text.isBlank()) {
			return List.of();
		}
		String normalized = Normalizer.normalize(text.toLowerCase(Locale.ROOT), Normalizer.Form.NFD)
			.replaceAll("\\p{M}+", "");
		List<Signal> signals = new ArrayList<>();
		if (PAYMENT.matcher(normalized).find()) {
			signals.add(new Signal(Kind.PAGAMENTO, true, "Pede pagamento ao candidato (taxa, curso ou material)"));
		}
		List<String> emailDomains = new ArrayList<>();
		Matcher emails = EMAIL.matcher(normalized);
		while (emails.find()) {
			emailDomains.add(emails.group(1));
		}
		boolean otherLinks = URL.matcher(normalized).results().map(match -> match.group(1)).anyMatch(host -> !host.equals("wa.me")
				&& !host.endsWith("whatsapp.com"));
		if (WHATSAPP.matcher(normalized).find() && emailDomains.isEmpty() && !otherLinks) {
			signals.add(new Signal(Kind.SO_WHATSAPP, false, "Contato só por WhatsApp, sem site ou e-mail da empresa"));
		}
		emailDomains.stream()
			.filter(FREE_EMAIL_DOMAINS::contains)
			.findFirst()
			.ifPresent(domain -> signals.add(new Signal(Kind.EMAIL_GRATUITO, false,
					"E-mail de contato gratuito (" + domain + ") no lugar do domínio da empresa")));
		return List.copyOf(signals);
	}

	public enum Kind {

		PAGAMENTO, SO_WHATSAPP, EMAIL_GRATUITO

	}

	/**
	 * @param strong sozinho já indica golpe (pedido de pagamento); os fracos pesam juntos com outros sinais
	 * @param reason frase curta para mostrar ao usuário
	 */
	public record Signal(Kind kind, boolean strong, String reason) {
	}

}
