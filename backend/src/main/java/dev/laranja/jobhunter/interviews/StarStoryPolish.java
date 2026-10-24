package dev.laranja.jobhunter.interviews;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import org.jspecify.annotations.Nullable;

/**
 * Sugestão de texto lapidado para uma história; só vale depois que você aceita e salva.
 *
 * @param notes o que mudou e o que você precisa completar (os marcadores entre colchetes)
 * @param warnings números que apareceram na versão lapidada sem estar no seu texto, para você conferir
 */
@Schema(name = "StarStoryPolish")
public record StarStoryPolish(String situation, String task, String action, String result,
		@Nullable String reflection, String notes, List<String> warnings) {
}
