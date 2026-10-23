package dev.laranja.jobhunter.inbox.internal;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import dev.laranja.jobhunter.inbox.GmailNotConnectedException;
import dev.laranja.jobhunter.inbox.GmailProperties;
import dev.laranja.jobhunter.inbox.internal.GmailDtos.TokenResponse;
import dev.laranja.jobhunter.shared.jdbc.JdbcRows;
import dev.laranja.jobhunter.shared.security.SecretCipher;
import org.jspecify.annotations.Nullable;

import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Fluxo OAuth do Google (authorization code com acesso offline) e guarda dos tokens, cifrados no banco. O
 * access token é renovado com o refresh token quando está para vencer.
 */
@Component
public class GmailCredentials {

	private static final String PROVIDER = "GOOGLE";

	private static final Duration EXPIRY_MARGIN = Duration.ofMinutes(1);

	private final GmailProperties properties;

	private final JdbcClient jdbc;

	private final SecretCipher cipher;

	private final RestClient http;

	private final Clock clock;

	GmailCredentials(GmailProperties properties, JdbcClient jdbc, SecretCipher cipher, RestClient.Builder http,
			Clock clock) {
		this.properties = properties;
		this.jdbc = jdbc;
		this.cipher = cipher;
		this.http = http.build();
		this.clock = clock;
	}

	/** URL de consentimento do Google; {@code prompt=consent} garante um refresh token novo a cada conexão. */
	public String authorizationUrl(String state) {
		requireConfigured();
		return UriComponentsBuilder.fromUri(this.properties.authUri())
			.queryParam("client_id", this.properties.clientId())
			.queryParam("redirect_uri", this.properties.redirectUri())
			.queryParam("response_type", "code")
			.queryParam("scope", GmailProperties.SCOPE)
			.queryParam("access_type", "offline")
			.queryParam("prompt", "consent")
			.queryParam("include_granted_scopes", "true")
			.queryParam("state", state)
			.encode()
			.toUriString();
	}

	/** Troca o código da autorização pelos tokens e os guarda cifrados. */
	public void completeAuthorization(String code) {
		requireConfigured();
		MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
		form.add("code", code);
		form.add("client_id", this.properties.clientId());
		form.add("client_secret", this.properties.clientSecret());
		form.add("redirect_uri", this.properties.redirectUri().toString());
		form.add("grant_type", "authorization_code");
		TokenResponse token = requestToken(form);
		if (token.refreshToken() == null) {
			throw new GmailNotConnectedException(
					"O Google não devolveu um refresh token. Remova o acesso do app na sua conta Google e conecte de novo.");
		}
		Instant now = this.clock.instant();
		this.jdbc.sql("""
				insert into oauth_credential (provider, access_token_enc, access_token_expires_at, refresh_token_enc,
				                              scopes, updated_at)
				values (:provider, :access, :expiresAt, :refresh, :scopes, :now)
				on conflict (provider) do update
				set access_token_enc = excluded.access_token_enc, access_token_expires_at = excluded.access_token_expires_at,
				    refresh_token_enc = excluded.refresh_token_enc, scopes = excluded.scopes,
				    updated_at = excluded.updated_at, account_email = null
				""")
			.param("provider", PROVIDER)
			.param("access", this.cipher.encrypt(token.accessToken()))
			.param("expiresAt", Timestamp.from(now.plusSeconds(token.expiresIn())))
			.param("refresh", this.cipher.encrypt(token.refreshToken()))
			.param("scopes", (token.scope() != null) ? token.scope() : GmailProperties.SCOPE)
			.param("now", Timestamp.from(now))
			.update();
	}

	public void rememberAccount(String email) {
		this.jdbc.sql("update oauth_credential set account_email = :email where provider = :provider")
			.param("email", email)
			.param("provider", PROVIDER)
			.update();
	}

	public boolean connected() {
		return stored().isPresent();
	}

	public Optional<String> accountEmail() {
		return stored().map(Stored::accountEmail);
	}

	public void disconnect() {
		this.jdbc.sql("delete from oauth_credential where provider = :provider").param("provider", PROVIDER).update();
	}

	/** Access token válido, renovado se necessário. */
	public String accessToken() {
		Stored credential = stored().orElseThrow(() -> new GmailNotConnectedException("Conecte o Gmail nas configurações."));
		Instant now = this.clock.instant();
		if (credential.accessToken() != null && credential.expiresAt() != null
				&& credential.expiresAt().minus(EXPIRY_MARGIN).isAfter(now)) {
			return this.cipher.decrypt(credential.accessToken());
		}
		MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
		form.add("client_id", this.properties.clientId());
		form.add("client_secret", this.properties.clientSecret());
		form.add("refresh_token", this.cipher.decrypt(credential.refreshToken()));
		form.add("grant_type", "refresh_token");
		TokenResponse token = requestToken(form);
		this.jdbc.sql("""
				update oauth_credential
				set access_token_enc = :access, access_token_expires_at = :expiresAt, updated_at = :now
				where provider = :provider
				""")
			.param("access", this.cipher.encrypt(token.accessToken()))
			.param("expiresAt", Timestamp.from(now.plusSeconds(token.expiresIn())))
			.param("now", Timestamp.from(now))
			.param("provider", PROVIDER)
			.update();
		return token.accessToken();
	}

	private TokenResponse requestToken(MultiValueMap<String, String> form) {
		try {
			return this.http.post()
				.uri(this.properties.tokenUri())
				.contentType(MediaType.APPLICATION_FORM_URLENCODED)
				.body(form)
				.retrieve()
				.body(TokenResponse.class);
		}
		catch (HttpClientErrorException ex) {
			// invalid_grant: o refresh token foi revogado ou expirou (no modo "Testing" do Google, em 7 dias).
			throw new GmailNotConnectedException("A autorização do Gmail expirou ou foi revogada. Conecte de novo.");
		}
	}

	private void requireConfigured() {
		if (!this.properties.configured()) {
			throw new GmailNotConnectedException(
					"Configure JOBHUNTER_GMAIL_CLIENT_ID e JOBHUNTER_GMAIL_CLIENT_SECRET para conectar o Gmail.");
		}
	}

	private Optional<Stored> stored() {
		return this.jdbc.sql("""
				select account_email, access_token_enc, access_token_expires_at, refresh_token_enc
				from oauth_credential where provider = :provider
				""")
			.param("provider", PROVIDER)
			.query((rs, row) -> new Stored(rs.getString("account_email"), rs.getString("access_token_enc"),
					JdbcRows.instant(rs, "access_token_expires_at"), rs.getString("refresh_token_enc")))
			.optional();
	}

	private record Stored(@Nullable String accountEmail, @Nullable String accessToken, @Nullable Instant expiresAt,
			String refreshToken) {
	}

}
