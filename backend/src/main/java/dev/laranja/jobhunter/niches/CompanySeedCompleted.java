package dev.laranja.jobhunter.niches;

import org.jspecify.annotations.Nullable;

/**
 * Uma importação de base pública terminou.
 *
 * @param error mensagem quando a importação falhou
 */
public record CompanySeedCompleted(String source, String label, int processed, int created, @Nullable String error) {
}
