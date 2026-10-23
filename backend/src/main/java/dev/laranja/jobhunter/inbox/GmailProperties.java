package dev.laranja.jobhunter.inbox;

import java.net.URI;
import java.util.List;
import java.util.regex.Pattern;

import org.jspecify.annotations.Nullable;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Integração com o Gmail. Sem {@code clientId}, a conexão fica desabilitada e a coleta segue sem o Gmail.
 *
 * @param label label do Gmail que concentra alertas e retornos (filtros do próprio Gmail aplicam a label)
 * @param initialSyncDays quantos dias para trás a primeira sincronização busca
 * @param alertRules remetentes (e assuntos) que identificam e-mails de alerta de vaga
 */
@ConfigurationProperties("jobhunter.gmail")
public record GmailProperties(@Nullable String clientId, @Nullable String clientSecret, URI redirectUri,
		String afterConnectUrl, @DefaultValue("jobs") String label, @DefaultValue("30") int initialSyncDays,
		@DefaultValue("https://accounts.google.com/o/oauth2/v2/auth") URI authUri,
		@DefaultValue("https://oauth2.googleapis.com/token") URI tokenUri,
		@DefaultValue("https://gmail.googleapis.com") URI apiBaseUrl, @Nullable List<AlertRule> alertRules) {

	public static final String SCOPE = "https://www.googleapis.com/auth/gmail.readonly";

	public boolean configured() {
		return this.clientId != null && !this.clientId.isBlank() && this.clientSecret != null
				&& !this.clientSecret.isBlank();
	}

	/** Regras padrão: alertas de vaga do LinkedIn e da Gupy. Os e-mails de candidatura da Gupy ficam de fora. */
	public List<AlertRule> effectiveAlertRules() {
		if (this.alertRules != null && !this.alertRules.isEmpty()) {
			return this.alertRules;
		}
		return List.of(new AlertRule("(?i)^(jobalerts|jobs-listings|jobs)-noreply@linkedin\\.com$", null),
				new AlertRule("(?i)@([a-z0-9-]+\\.)*gupy\\.(io|com\\.br)$",
						"(?i)^(?!.*candidatura).*(vagas?|oportunidades?).*$"));
	}

	/**
	 * @param senderPattern regex aplicada ao endereço do remetente
	 * @param subjectPattern regex opcional aplicada ao assunto
	 */
	public record AlertRule(String senderPattern, @Nullable String subjectPattern) {

		public boolean matches(String sender, @Nullable String subject) {
			if (!Pattern.compile(this.senderPattern).matcher(sender).find()) {
				return false;
			}
			return this.subjectPattern == null
					|| (subject != null && Pattern.compile(this.subjectPattern).matcher(subject).find());
		}

	}

}
