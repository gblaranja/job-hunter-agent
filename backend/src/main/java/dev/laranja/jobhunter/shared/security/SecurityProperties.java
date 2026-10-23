package dev.laranja.jobhunter.shared.security;

import jakarta.validation.constraints.NotBlank;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Usuário único do backoffice.
 *
 * @param username login
 * @param passwordHash hash no formato do DelegatingPasswordEncoder, por exemplo {@code {bcrypt}$2a$10$...}
 */
@Validated
@ConfigurationProperties("jobhunter.security")
record SecurityProperties(@NotBlank String username, @NotBlank String passwordHash) {
}
