package dev.laranja.jobhunter.shared.security;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Login e logout são tratados pelo Spring Security; aqui fica só a consulta da sessão atual. */
@RestController
@RequestMapping("/api/auth")
class AuthController {

	@GetMapping("/me")
	CurrentUser me(Authentication authentication) {
		return CurrentUser.of(authentication);
	}

	/**
	 * @param username login com senha ou e-mail da conta Google
	 * @param displayName o que a tela mostra: o nome da conta Google, ou o próprio {@code username}
	 */
	record CurrentUser(String username, String displayName) {

		static CurrentUser of(Authentication authentication) {
			String displayName = (authentication.getPrincipal() instanceof GoogleAccount account)
					? account.displayName() : authentication.getName();
			return new CurrentUser(authentication.getName(), displayName);
		}

	}

}
