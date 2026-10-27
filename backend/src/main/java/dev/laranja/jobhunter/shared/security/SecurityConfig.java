package dev.laranja.jobhunter.shared.security;

import java.io.IOException;
import java.util.List;

import jakarta.servlet.http.HttpServletResponse;
import tools.jackson.databind.json.JsonMapper;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.authentication.logout.HttpStatusReturningLogoutSuccessHandler;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfFilter;

/**
 * Login do dono da instalação, com senha ou com Google, por sessão (cookie) e CSRF no modo SPA: o token vai no cookie {@code XSRF-TOKEN} e o
 * frontend o devolve no header {@code X-XSRF-TOKEN}. Nenhuma rota da API redireciona: sem sessão, a
 * resposta é 401.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties({ SecurityProperties.class, GoogleLoginProperties.class,
		SecretCipher.EncryptionProperties.class })
class SecurityConfig {

	/** Endpoints que aceitam o token pessoal da extensão: só importar e salvar uma vaga. */
	static final List<String> API_TOKEN_PATHS = List.of("/api/jobs/import/preview", "/api/jobs");

	/**
	 * Cadeia da extensão do navegador: só {@code POST} nos endpoints de {@link #API_TOKEN_PATHS} com
	 * {@code Authorization: Bearer}. Sem sessão e sem CSRF (o token não vai em cookie). Com o Bearer em qualquer
	 * outro endpoint, a requisição cai na cadeia normal e recebe 401.
	 */
	@Bean
	@Order(1)
	SecurityFilterChain apiTokenFilterChain(HttpSecurity http, PersonalApiTokens tokens, SecurityProperties properties)
			throws Exception {
		http.securityMatcher(request -> "POST".equals(request.getMethod())
				&& API_TOKEN_PATHS.contains(request.getRequestURI().substring(request.getContextPath().length()))
				&& PersonalApiTokenFilter.bearer(request) != null)
			.authorizeHttpRequests(auth -> auth.anyRequest().hasAuthority("API_TOKEN"))
			.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
			.csrf(csrf -> csrf.disable())
			.exceptionHandling(exceptions -> exceptions
				.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
			.addFilterBefore(new PersonalApiTokenFilter(tokens, properties.username()),
					UsernamePasswordAuthenticationFilter.class);
		return http.build();
	}

	@Bean
	@Order(2)
	SecurityFilterChain securityFilterChain(HttpSecurity http, JsonMapper jsonMapper,
			SecurityContextRepository securityContextRepository) throws Exception {
		http.authorizeHttpRequests(auth -> auth
			.requestMatchers("/actuator/health/**", "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html", "/error",
					"/api/auth/providers", "/api/auth/google", "/api/auth/google/callback")
			.permitAll()
			.anyRequest()
			.authenticated())
			.formLogin(form -> form.loginProcessingUrl("/api/auth/login")
				.successHandler((request, response, authentication) -> writeJson(jsonMapper, response, HttpStatus.OK,
						AuthController.CurrentUser.of(authentication)))
				.failureHandler((request, response, exception) -> writeJson(jsonMapper, response,
						HttpStatus.UNAUTHORIZED,
						ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, "Usuário ou senha inválidos."))))
			.logout(logout -> logout.logoutUrl("/api/auth/logout")
				.logoutSuccessHandler(new HttpStatusReturningLogoutSuccessHandler()))
			.exceptionHandling(exceptions -> exceptions
				.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
			.securityContext(context -> context.securityContextRepository(securityContextRepository))
			.csrf(csrf -> csrf.spa())
			.addFilterAfter(new CsrfCookieFilter(), CsrfFilter.class);
		return http.build();
	}

	/** Compartilhado com o login com Google, que abre a sessão fora do formulário. */
	@Bean
	SecurityContextRepository securityContextRepository() {
		return new HttpSessionSecurityContextRepository();
	}

	@Bean
	UserDetailsService userDetailsService(SecurityProperties properties) {
		return new InMemoryUserDetailsManager(
				User.withUsername(properties.username()).password(properties.passwordHash()).roles("OWNER").build());
	}

	@Bean
	PasswordEncoder passwordEncoder() {
		return PasswordEncoderFactories.createDelegatingPasswordEncoder();
	}

	private static void writeJson(JsonMapper jsonMapper, HttpServletResponse response, HttpStatus status, Object body)
			throws IOException {
		response.setStatus(status.value());
		response.setContentType(
				body instanceof ProblemDetail ? MediaType.APPLICATION_PROBLEM_JSON_VALUE : MediaType.APPLICATION_JSON_VALUE);
		jsonMapper.writeValue(response.getOutputStream(), body);
	}

}
