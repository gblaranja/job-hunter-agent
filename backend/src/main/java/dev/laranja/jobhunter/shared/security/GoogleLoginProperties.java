package dev.laranja.jobhunter.shared.security;

import java.net.URI;

import org.jspecify.annotations.Nullable;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Login com Google. Por padrão usa a mesma credencial OAuth do Gmail; só entra a conta com o e-mail autorizado,
 * já que cada instalação tem um único dono. Sem {@code allowedEmail}, o botão não aparece.
 *
 * @param allowedEmail e-mail da conta Google que pode entrar
 * @param afterLoginUrl para onde o navegador volta depois do login (o frontend)
 */
@ConfigurationProperties("jobhunter.security.google")
record GoogleLoginProperties(@Nullable String clientId, @Nullable String clientSecret, URI redirectUri,
		@Nullable String allowedEmail, @DefaultValue("/today") String afterLoginUrl,
		@DefaultValue("/login") String loginUrl,
		@DefaultValue("https://accounts.google.com/o/oauth2/v2/auth") URI authUri,
		@DefaultValue("https://oauth2.googleapis.com/token") URI tokenUri,
		@DefaultValue("https://openidconnect.googleapis.com/v1/userinfo") URI userInfoUri) {

	static final String SCOPE = "openid email profile";

	boolean configured() {
		return notBlank(this.clientId) && notBlank(this.clientSecret) && notBlank(this.allowedEmail);
	}

	private static boolean notBlank(@Nullable String value) {
		return value != null && !value.isBlank();
	}

}
