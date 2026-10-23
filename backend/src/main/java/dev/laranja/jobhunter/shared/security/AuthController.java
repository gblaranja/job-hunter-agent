package dev.laranja.jobhunter.shared.security;

import java.security.Principal;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Login e logout são tratados pelo Spring Security; aqui fica só a consulta da sessão atual. */
@RestController
@RequestMapping("/api/auth")
class AuthController {

	@GetMapping("/me")
	CurrentUser me(Principal principal) {
		return new CurrentUser(principal.getName());
	}

	record CurrentUser(String username) {
	}

}
