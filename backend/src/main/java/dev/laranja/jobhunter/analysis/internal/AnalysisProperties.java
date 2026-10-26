package dev.laranja.jobhunter.analysis.internal;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * @param knockoutCap teto do score quando a vaga tem um eliminatório que a pessoa não atende
 */
@ConfigurationProperties("jobhunter.analysis")
public record AnalysisProperties(@DefaultValue("40") int knockoutCap) {
}
