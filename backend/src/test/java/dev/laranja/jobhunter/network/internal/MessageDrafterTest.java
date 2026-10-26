package dev.laranja.jobhunter.network.internal;

import java.time.Instant;

import dev.laranja.jobhunter.jobs.JobOrigin;
import dev.laranja.jobhunter.jobs.JobScope;
import dev.laranja.jobhunter.jobs.JobView;
import dev.laranja.jobhunter.jobs.WorkModel;
import dev.laranja.jobhunter.network.ContactRole;
import dev.laranja.jobhunter.network.ContactSource;
import dev.laranja.jobhunter.network.ContactView;
import dev.laranja.jobhunter.network.DraftKind;
import dev.laranja.jobhunter.network.MessageDraft;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;

class MessageDrafterTest {

	private static final JobView JOB = new JobView(1, JobOrigin.MANUAL, "Desenvolvedor Java Sênior", "Banco Exemplo",
			"Remoto", WorkModel.REMOTO, JobScope.NACIONAL, null, null,
			"Buscamos pessoa desenvolvedora com experiência em Java para o time de crédito.", Instant.now());

	private static final ContactView ANA = new ContactView(3, "Ana Lima", ContactRole.CONEXAO, 5L, "Banco Exemplo",
			"Engineering Manager", null, null, null, ContactSource.LINKEDIN_EXPORT, null, null, Instant.now());

	@ParameterizedTest
	@EnumSource(DraftKind.class)
	void templatesNameTheJobAndLeaveTheSignatureForYou(DraftKind kind) {
		MessageDraft draft = MessageDrafter.Templates.draft(kind, JOB, null, ANA, "pt");

		assertThat(draft.text()).contains("Olá, Ana!").contains("Desenvolvedor Java Sênior").contains("[seu nome]");
		assertThat(draft.source()).isEqualTo("MODELO");
		assertThat(draft.linkedinNote() != null).isEqualTo(kind == DraftKind.PEDIDO_INDICACAO);
	}

	@Test
	void theReferralInviteFitsTheLinkedInLimit() {
		MessageDraft draft = MessageDrafter.Templates.draft(DraftKind.PEDIDO_INDICACAO, JOB, null, ANA, "pt");

		assertThat(draft.linkedinNote()).hasSizeLessThanOrEqualTo(MessageDraft.LINKEDIN_NOTE_LIMIT);
		assertThat(MessageDrafter.limitNote("palavra ".repeat(60))).hasSizeLessThanOrEqualTo(300).endsWith("…");
	}

	@Test
	void writesInTheLanguageOfTheJob() {
		JobView english = new JobView(2, JobOrigin.MANUAL, "Backend Engineer", "Acme", null, WorkModel.REMOTO,
				JobScope.INTERNACIONAL, null, null, "You will work with our team and the experience with Kafka.",
				Instant.now());

		assertThat(MessageDrafter.language(JOB)).isEqualTo("pt");
		assertThat(MessageDrafter.language(english)).isEqualTo("en");
		assertThat(MessageDrafter.Templates.draft(DraftKind.FOLLOW_UP, english, null, null, "en").text())
			.startsWith("Hi,");
	}

}
