package dev.laranja.jobhunter.shared.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;

import org.jspecify.annotations.Nullable;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Token pessoal de API, usado pela extensão do navegador. É gerado em Configurações e mostrado uma vez; só o hash
 * SHA-256 fica gravado (o token tem 256 bits aleatórios, então não precisa de um hash lento). Gerar outro invalida o
 * anterior.
 */
@Service
@Transactional
class PersonalApiTokens {

	static final String PREFIX = "jh_";

	private final SecureRandom random = new SecureRandom();

	private final JdbcClient jdbc;

	private final Clock clock;

	PersonalApiTokens(JdbcClient jdbc, Clock clock) {
		this.jdbc = jdbc;
		this.clock = clock;
	}

	/** Gera um token novo no lugar do atual e devolve o texto, que não fica gravado. */
	String generate() {
		byte[] bytes = new byte[32];
		this.random.nextBytes(bytes);
		String token = PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
		this.jdbc.sql("delete from personal_api_token").update();
		this.jdbc.sql("""
				insert into personal_api_token (token_hash, hint, created_at) values (:hash, :hint, :now)
				""")
			.param("hash", hash(token))
			.param("hint", token.substring(token.length() - 4))
			.param("now", Timestamp.from(this.clock.instant()))
			.update();
		return token;
	}

	void revoke() {
		this.jdbc.sql("delete from personal_api_token").update();
	}

	@Transactional(readOnly = true)
	Optional<TokenStatus> status() {
		return this.jdbc.sql("select hint, created_at, last_used_at from personal_api_token")
			.query((rs, row) -> new TokenStatus(rs.getString("hint"), rs.getTimestamp("created_at").toInstant(),
					(rs.getTimestamp("last_used_at") != null) ? rs.getTimestamp("last_used_at").toInstant() : null))
			.optional();
	}

	/** Confere o token e anota o uso. */
	boolean authenticate(String token) {
		if (!token.startsWith(PREFIX)) {
			return false;
		}
		return this.jdbc.sql("update personal_api_token set last_used_at = :now where token_hash = :hash")
			.param("now", Timestamp.from(this.clock.instant()))
			.param("hash", hash(token))
			.update() > 0;
	}

	private static String hash(String token) {
		try {
			return HexFormat.of()
				.formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));
		}
		catch (NoSuchAlgorithmException ex) {
			throw new IllegalStateException(ex);
		}
	}

	/**
	 * @param hint os 4 últimos caracteres, para reconhecer o token
	 */
	record TokenStatus(String hint, Instant createdAt, @Nullable Instant lastUsedAt) {
	}

}
