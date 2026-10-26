package dev.laranja.jobhunter.shared.money;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.Locale;

/** Valores em texto no formato brasileiro, para mensagens e rascunhos ("R$ 18.000 por mês"). */
public final class MoneyFormat {

	private static final Locale BRAZIL = Locale.forLanguageTag("pt-BR");

	private MoneyFormat() {
	}

	public static String amount(BigDecimal value, String currency) {
		NumberFormat format = NumberFormat.getNumberInstance(BRAZIL);
		format.setMinimumFractionDigits(value.stripTrailingZeros().scale() > 0 ? 2 : 0);
		format.setMaximumFractionDigits(2);
		return symbol(currency) + " " + format.format(value);
	}

	public static String amount(BigDecimal value, String currency, PayPeriod period) {
		return amount(value, currency) + " " + switch (period) {
			case MENSAL -> "por mês";
			case ANUAL -> "por ano";
			case HORA -> "por hora";
		};
	}

	static String symbol(String currency) {
		return switch (currency.toUpperCase(Locale.ROOT)) {
			case "BRL" -> "R$";
			case "USD" -> "US$";
			case "EUR" -> "€";
			case "GBP" -> "£";
			default -> currency.toUpperCase(Locale.ROOT);
		};
	}

}
