package dev.laranja.jobhunter.jobs;

import dev.laranja.jobhunter.jobs.internal.JobFamilyClassifier;
import org.jspecify.annotations.Nullable;

/**
 * Família de cargo, classificada pelo título. Os mesmos valores do campo {@code jobFamily} do
 * {@code extract-requirements.v2}: a Claude refina o que as regras não resolvem. Os perfis de busca escolhem as
 * famílias de interesse, e as vagas fora delas saem da caixa com o motivo {@link DismissReason#SEM_PERFIL_TECNICO}.
 */
public enum JobFamily {

	BACKEND("Backend"), FRONTEND("Frontend"), FULLSTACK("Full stack"), MOBILE("Mobile"), DADOS("Dados"),
	DEVOPS_SRE("SRE/DevOps"), QA("QA"), SEGURANCA("Segurança"), GESTAO("Gestão"),

	/** Cargo fora de tecnologia (vendas, RH, financeiro, atendimento...). */
	FORA_DE_TI("Fora de TI"),

	/** Tecnologia, mas sem uma das famílias acima (suporte, design, produto, "engenheiro de software" genérico). */
	OUTRA("Outra");

	private final String label;

	JobFamily(String label) {
		this.label = label;
	}

	/** Nome para as telas e o detalhe do descarte. */
	public String label() {
		return this.label;
	}

	/** Família pelo título, ou nula quando as regras não reconhecem nenhuma. */
	public static @Nullable JobFamily ofTitle(@Nullable String title) {
		return JobFamilyClassifier.classify(title);
	}

}
