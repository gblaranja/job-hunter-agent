package dev.laranja.jobhunter.shared.security;

import java.io.IOException;
import java.util.Map;

import jakarta.servlet.http.HttpServletResponse;
import tools.jackson.databind.json.JsonMapper;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.logout.HttpStatusReturningLogoutSuccessHandler;
import org.springframework.security.web.csrf.CsrfFilter;

/**
 * Login único por sessão (cookie) com CSRF no modo SPA: o token vai no cookie {@code XSRF-TOKEN} e o
 * frontend o devolve no header {@code X-XSRF-TOKEN}. Nenhuma rota da API redireciona: sem sessão, a
 * resposta é 401.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(SecurityProperties.class)
class SecurityConfig {

	@Bean
	SecurityFilterChain securityFilterChain(HttpSecurity http, JsonMapper jsonMapper) throws Exception {
		http.authorizeHttpRequests(auth -> auth
			.requestMatchers("/actuator/health/**", "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html", "/error")
			.permitAll()
			.anyRequest()
			.authenticated())
			.formLogin(form -> form.loginProcessingUrl("/api/auth/login")
				.successHandler((request, response, authentication) -> writeJson(jsonMapper, response, HttpStatus.OK,
						Map.of("username", authentication.getName())))
				.failureHandler((request, response, exception) -> writeJson(jsonMapper, response,
						HttpStatus.UNAUTHORIZED,
						ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, "Usuário ou senha inválidos."))))
			.logout(logout -> logout.logoutUrl("/api/auth/logout")
				.logoutSuccessHandler(new HttpStatusReturningLogoutSuccessHandler()))
			.exceptionHandling(exceptions -> exceptions
				.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
			.csrf(csrf -> csrf.spa())
			.addFilterAfter(new CsrfCookieFilter(), CsrfFilter.class);
		return http.build();
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
