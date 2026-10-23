package dev.laranja.jobhunter.inbox.internal;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;

import dev.laranja.jobhunter.inbox.GmailProperties;
import dev.laranja.jobhunter.inbox.internal.GmailDtos.Body;
import dev.laranja.jobhunter.inbox.internal.GmailDtos.MessagePart;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GmailParsingTest {

	@Test
	void senderAddressIsExtractedFromTheFromHeader() {
		assertThat(GmailAccess.senderAddress("LinkedIn Job Alerts <JobAlerts-NoReply@linkedin.com>"))
			.isEqualTo("jobalerts-noreply@linkedin.com");
		assertThat(GmailAccess.senderAddress("recrutadora@empresa.com")).isEqualTo("recrutadora@empresa.com");
	}

	@Test
	void htmlPartIsPreferredInMultipartMessages() {
		MessagePart plain = part("text/plain", "texto");
		MessagePart html = part("text/html", "<p>Olá, <a href=\"https://x\">vaga</a></p>");
		MessagePart root = new MessagePart("multipart/alternative", null, null, List.of(plain, html));

		assertThat(MimeBodyExtractor.html(root)).hasValue("<p>Olá, <a href=\"https://x\">vaga</a></p>");
	}

	@Test
	void plainTextIsEscapedWhenThereIsNoHtml() {
		assertThat(MimeBodyExtractor.html(part("text/plain", "1 < 2"))).hasValue("<pre>1 &lt; 2</pre>");
	}

	@Test
	void defaultAlertRulesKeepGupyApplicationEmailsOut() {
		GmailProperties properties = new GmailProperties(null, null, null, "/settings", "jobs", 30, null, null, null,
				null);

		assertThat(properties.effectiveAlertRules())
			.anyMatch(rule -> rule.matches("jobalerts-noreply@linkedin.com", "Novas vagas"));
		assertThat(properties.effectiveAlertRules())
			.anyMatch(rule -> rule.matches("noreply@gupy.io", "5 novas vagas para você"));
		assertThat(properties.effectiveAlertRules())
			.noneMatch(rule -> rule.matches("noreply@gupy.io", "Recebemos sua candidatura"));
		assertThat(properties.effectiveAlertRules())
			.noneMatch(rule -> rule.matches("messages-noreply@linkedin.com", "Você tem uma nova mensagem"));
	}

	private static MessagePart part(String mimeType, String content) {
		String data = Base64.getUrlEncoder().encodeToString(content.getBytes(StandardCharsets.UTF_8));
		return new MessagePart(mimeType, null, new Body(data, content.length()), null);
	}

}
