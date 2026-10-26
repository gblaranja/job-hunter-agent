package dev.laranja.jobhunter.shared.security;

import java.time.Instant;

import org.jspecify.annotations.Nullable;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Token pessoal da extensão do navegador, gerenciado em Configurações (com a sessão normal, nunca com o token). */
@RestController
@RequestMapping("/api/auth/api-token")
class PersonalApiTokenController {

	private final PersonalApiTokens tokens;

	PersonalApiTokenController(PersonalApiTokens tokens) {
		this.tokens = tokens;
	}

	@GetMapping
	ApiTokenStatus status() {
		return this.tokens.status()
			.map(status -> new ApiTokenStatus(true, status.hint(), status.createdAt(), status.lastUsedAt()))
			.orElse(new ApiTokenStatus(false, null, null, null));
	}

	/** Gera um token novo, que invalida o anterior. O texto só aparece nesta resposta. */
	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	GeneratedApiToken generate() {
		return new GeneratedApiToken(this.tokens.generate());
	}

	@DeleteMapping
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void revoke() {
		this.tokens.revoke();
	}

	/**
	 * @param hint os 4 últimos caracteres do token
	 */
	record ApiTokenStatus(boolean exists, @Nullable String hint, @Nullable Instant createdAt,
			@Nullable Instant lastUsedAt) {
	}

	/** @param token mostrado só agora; cole na extensão */
	record GeneratedApiToken(String token) {
	}

}
