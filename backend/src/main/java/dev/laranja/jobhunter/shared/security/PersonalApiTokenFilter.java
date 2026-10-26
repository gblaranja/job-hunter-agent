package dev.laranja.jobhunter.shared.security;

import java.io.IOException;
import java.util.List;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/** Autentica pelo header {@code Authorization: Bearer jh_...} as requisições da cadeia do token pessoal. */
class PersonalApiTokenFilter extends OncePerRequestFilter {

	private final PersonalApiTokens tokens;

	private final String username;

	PersonalApiTokenFilter(PersonalApiTokens tokens, String username) {
		this.tokens = tokens;
		this.username = username;
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		String token = bearer(request);
		if (token != null && this.tokens.authenticate(token)) {
			var authentication = UsernamePasswordAuthenticationToken.authenticated(this.username, null,
					List.of(new SimpleGrantedAuthority("ROLE_OWNER"), new SimpleGrantedAuthority("API_TOKEN")));
			SecurityContextHolder.getContext().setAuthentication(authentication);
		}
		chain.doFilter(request, response);
	}

	static String bearer(HttpServletRequest request) {
		String header = request.getHeader("Authorization");
		return (header != null && header.regionMatches(true, 0, "Bearer ", 0, 7)) ? header.substring(7).strip() : null;
	}

}
