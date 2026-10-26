package dev.laranja.jobhunter.analysis;

import java.math.BigDecimal;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import org.jspecify.annotations.Nullable;

/**
 * Remuneração informada na vaga, como está nela (sem conversão de moeda ou de período). É também o schema da saída
 * do LLM.
 */
public record JobSalary(
		@JsonPropertyDescription("Valor mínimo, em unidades da moeda (12000)") @Nullable BigDecimal min,
		@JsonPropertyDescription("Valor máximo; igual ao mínimo quando a vaga dá um valor só") @Nullable BigDecimal max,
		@JsonPropertyDescription("Código ISO da moeda: BRL, USD, EUR") String currency,
		@JsonPropertyDescription("MENSAL, ANUAL ou HORA") String period,
		@JsonPropertyDescription("CLT, PJ ou CONTRACTOR; nulo se a vaga não diz") @Nullable String regime,
		@JsonPropertyDescription("Trecho da vaga com a remuneração") @Nullable String text) {

	/** A saída do LLM às vezes vem com valores zerados em vez de nula: sem nenhum valor, não há salário. */
	@JsonIgnore
	public boolean isInformed() {
		return positive(this.min) || positive(this.max);
	}

	private static boolean positive(@Nullable BigDecimal value) {
		return value != null && value.signum() > 0;
	}

}
