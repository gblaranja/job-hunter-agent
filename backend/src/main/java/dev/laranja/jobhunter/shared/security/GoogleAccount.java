package dev.laranja.jobhunter.shared.security;

import java.io.Serializable;

import org.jspecify.annotations.Nullable;

import org.springframework.security.core.AuthenticatedPrincipal;

/**
 * Quem entrou com Google: o e-mail identifica a sessão e o nome é o que a tela mostra.
 *
 * @param name nome da conta Google, quando o Google o informa
 */
record GoogleAccount(String email, @Nullable String name) implements AuthenticatedPrincipal, Serializable {

	@Override
	public String getName() {
		return this.email;
	}

	String displayName() {
		return (this.name != null && !this.name.isBlank()) ? this.name : this.email;
	}

}
