package dev.laranja.jobhunter.analysis.internal;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import dev.laranja.jobhunter.analysis.JobSalary;
import org.jspecify.annotations.Nullable;

/**
 * Requisitos extraídos de uma vaga. É também o schema da saída estruturada do LLM, por isso os campos têm
 * descrição.
 */
public record JobRequirements(
		@JsonPropertyDescription("ESTAGIO, JUNIOR, PLENO, SENIOR ou ESPECIALISTA; vazio se a vaga não disser") @Nullable String seniority,
		@JsonPropertyDescription("Tecnologias e competências obrigatórias, com a grafia usual (\"Spring Boot\", \"Kafka\")") List<String> requiredSkills,
		@JsonPropertyDescription("Diferenciais ou desejáveis") List<String> niceToHave,
		@JsonPropertyDescription("REMOTO, HIBRIDO, PRESENCIAL ou NAO_INFORMADO") String workModel,
		@JsonPropertyDescription("Resumo em até 3 linhas: o que a pessoa vai fazer, a stack e o contexto") String summary,
		@JsonPropertyDescription("CONTRATANTE se a vaga é da própria empresa, CONSULTORIA_TI se é para alocar em cliente, RECRUTAMENTO se é uma agência") String employerKind,
		@JsonPropertyDescription("Setores dos clientes citados, se for consultoria (ex.: bancos, varejo)") List<String> clientSectors,
		@JsonPropertyDescription("CLT, PJ, AMBOS ou NAO_INFORMADO") String contractType,
		@JsonPropertyDescription("Eliminatórios obrigatórios da vaga; lista vazia se não houver") List<KnockoutRequirement> knockouts,
		@JsonPropertyDescription("Remuneração informada; nula se a vaga não informa") @Nullable JobSalary salary,
		@JsonPropertyDescription("BACKEND, FRONTEND, FULLSTACK, MOBILE, DADOS, DEVOPS_SRE, QA, SEGURANCA, GESTAO, FORA_DE_TI ou OUTRA") @Nullable String jobFamily,
		@JsonPropertyDescription("BRASIL, LATAM, GLOBAL, PAIS_ESPECIFICO ou NAO_INFORMADO") @Nullable String internationalEligibility) {

	public JobRequirements {
		knockouts = (knockouts == null) ? List.of() : knockouts;
		salary = (salary != null && salary.isInformed()) ? salary : null;
	}

	/** Sem eliminatórios, salário e classificação (as análises de antes do extract-requirements v2). */
	public JobRequirements(@Nullable String seniority, List<String> requiredSkills, List<String> niceToHave,
			String workModel, @Nullable String summary, String employerKind, List<String> clientSectors,
			String contractType) {
		this(seniority, requiredSkills, niceToHave, workModel, summary, employerKind, clientSectors, contractType,
				List.of(), null, null, null);
	}

}
