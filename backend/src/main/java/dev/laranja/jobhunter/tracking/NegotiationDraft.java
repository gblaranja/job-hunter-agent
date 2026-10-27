package dev.laranja.jobhunter.tracking;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;
import org.jspecify.annotations.Nullable;

import java.math.BigDecimal;

/**
 * Rascunho da mensagem de negociação de uma proposta. Nada é enviado.
 *
 * @param source IA (Claude API) ou MODELO (texto fixo, sem a chave ou quando a IA citou um número que você não
 * informou)
 */
@Schema(name = "NegotiationDraft")
public record NegotiationDraft(String text, String source) {

	/**
	 * O que você quer pedir.
	 * @param targetSalary valor-base desejado, no mesmo período e moeda da proposta
	 * @param arguments seus argumentos (outra proposta, escopo maior, custo de mudança)
	 */
	@Schema(name = "NegotiationRequest")
	public record Request(@Nullable @DecimalMin("0.01") BigDecimal targetSalary,
			@Nullable @Size(max = 3000) String arguments) {
	}

}
