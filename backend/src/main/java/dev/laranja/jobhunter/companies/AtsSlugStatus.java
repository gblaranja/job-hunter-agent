package dev.laranja.jobhunter.companies;

/** Resultado da conferência do slug no ATS. */
public enum AtsSlugStatus {

	/** O ATS respondeu com o job board da empresa. */
	ENCONTRADO,

	/** O ATS respondeu que o job board não existe. */
	NAO_ENCONTRADO,

	/**
	 * Não deu para confirmar: o ATS ficou fora do ar, devolveu lista vazia sem distinguir empresa inexistente de empresa
	 * sem vagas (SmartRecruiters) ou não tem coleta automática.
	 */
	NAO_CONFIRMADO

}
