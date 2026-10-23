package dev.laranja.jobhunter.shared.security;

import java.net.URI;
import java.util.Locale;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * "Entrar com Google" (OpenID Connect, authorization code). O navegador vai ao Google e volta no callback; se a
 * conta tiver o e-mail autorizado e verificado, a sessão é aberta como o dono da instalação, igual ao login com
 * senha.
 */
@RestController
@RequestMapping("/api/auth")
class GoogleLoginController {

	private static final Logger log = LoggerFactory.getLogger(GoogleLoginController.class);

	private static final String STATE_ATTRIBUTE = GoogleLoginController.class.getName() + ".state";

	private final GoogleLoginProperties google;

	private final SecurityProperties owner;

	private final UserDetailsService users;

	private final SecurityContextRepository contexts;

	private final RestClient http;

	GoogleLoginController(GoogleLoginProperties google, SecurityProperties owner, UserDetailsService users,
			SecurityContextRepository contexts, RestClient.Builder http) {
		this.google = google;
		this.owner = owner;
		this.users = users;
		this.contexts = contexts;
		this.http = http.build();
	}

	/** Formas de login disponíveis, para a tela de login mostrar o botão do Google só quando configurado. */
	@GetMapping("/providers")
	LoginProviders providers() {
		return new LoginProviders(true, this.google.configured());
	}

	@GetMapping("/google")
	ResponseEntity<Void> start(HttpSession session) {
		if (!this.google.configured()) {
			return redirect(back("indisponivel"));
		}
		String state = UUID.randomUUID().toString();
		session.setAttribute(STATE_ATTRIBUTE, state);
		return redirect(UriComponentsBuilder.fromUri(this.google.authUri())
			.queryParam("client_id", this.google.clientId())
			.queryParam("redirect_uri", this.google.redirectUri())
			.queryParam("response_type", "code")
			.queryParam("scope", GoogleLoginProperties.SCOPE)
			.queryParam("prompt", "select_account")
			.queryParam("state", state)
			.encode()
			.build()
			.toUri());
	}

	@GetMapping("/google/callback")
	ResponseEntity<Void> callback(@RequestParam(required = false) @Nullable String code,
			@RequestParam(required = false) @Nullable String state,
			@RequestParam(required = false) @Nullable String error, HttpServletRequest request,
			HttpServletResponse response) {
		HttpSession session = request.getSession();
		Object expected = session.getAttribute(STATE_ATTRIBUTE);
		session.removeAttribute(STATE_ATTRIBUTE);
		if (!this.google.configured() || error != null || code == null || state == null || !state.equals(expected)) {
			log.warn("Login com Google cancelado ou inválido (error={}).", error);
			return redirect(back("erro"));
		}
		UserInfo user;
		try {
			user = userInfo(exchange(code));
		}
		catch (RestClientException ex) {
			log.warn("Falha ao concluir o login com Google: {}", ex.getMessage());
			return redirect(back("erro"));
		}
		if (!Boolean.TRUE.equals(user.emailVerified()) || user.email() == null
				|| !user.email().toLowerCase(Locale.ROOT).equals(this.google.allowedEmail().strip().toLowerCase(Locale.ROOT))) {
			log.warn("Login com Google recusado para {}.", user.email());
			return redirect(back("negado"));
		}
		signIn(request, response);
		return redirect(URI.create(this.google.afterLoginUrl()));
	}

	private String exchange(String code) {
		MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
		form.add("code", code);
		form.add("client_id", this.google.clientId());
		form.add("client_secret", this.google.clientSecret());
		form.add("redirect_uri", this.google.redirectUri().toString());
		form.add("grant_type", "authorization_code");
		TokenResponse token = this.http.post()
			.uri(this.google.tokenUri())
			.contentType(MediaType.APPLICATION_FORM_URLENCODED)
			.body(form)
			.retrieve()
			.body(TokenResponse.class);
		if (token == null || token.accessToken() == null) {
			throw new RestClientException("O Google não devolveu o access token.");
		}
		return token.accessToken();
	}

	private UserInfo userInfo(String accessToken) {
		UserInfo user = this.http.get()
			.uri(this.google.userInfoUri())
			.header("Authorization", "Bearer " + accessToken)
			.retrieve()
			.body(UserInfo.class);
		if (user == null) {
			throw new RestClientException("O Google não devolveu os dados da conta.");
		}
		return user;
	}

	/** Mesma sessão do login com senha: troca o id da sessão (contra fixation) e guarda o contexto. */
	private void signIn(HttpServletRequest request, HttpServletResponse response) {
		request.changeSessionId();
		UserDetails user = this.users.loadUserByUsername(this.owner.username());
		SecurityContext context = SecurityContextHolder.createEmptyContext();
		context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(user, null, user.getAuthorities()));
		SecurityContextHolder.setContext(context);
		this.contexts.saveContext(context, request, response);
	}

	private URI back(String result) {
		return UriComponentsBuilder.fromUriString(this.google.loginUrl()).queryParam("google", result).build().toUri();
	}

	private static ResponseEntity<Void> redirect(URI location) {
		return ResponseEntity.status(HttpStatus.FOUND).location(location).build();
	}

	/** @param google o botão "Entrar com Google" está disponível */
	record LoginProviders(boolean password, boolean google) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record TokenResponse(@JsonProperty("access_token") @Nullable String accessToken) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record UserInfo(@Nullable String email, @JsonProperty("email_verified") @Nullable Boolean emailVerified,
			@Nullable String name) {
	}

}
