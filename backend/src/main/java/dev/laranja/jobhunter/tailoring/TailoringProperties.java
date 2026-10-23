package dev.laranja.jobhunter.tailoring;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Currículos adaptados por vaga.
 *
 * @param autoGenerate gera a versão adaptada sozinho quando você marca interesse numa vaga (com a Claude API)
 */
@ConfigurationProperties("jobhunter.tailoring")
public record TailoringProperties(@DefaultValue("true") boolean autoGenerate) {
}
