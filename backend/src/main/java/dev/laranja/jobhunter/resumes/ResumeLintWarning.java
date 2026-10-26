package dev.laranja.jobhunter.resumes;

/**
 * Aviso do teste "o que o ATS lê" sobre uma versão do currículo.
 *
 * @param code SEM_TEXTO, SEM_EMAIL, SEM_TELEFONE, SECAO_AUSENTE, SEM_DATAS, DATAS_FORA_DE_ORDEM,
 * CARACTERES_ILEGIVEIS, TEXTO_CURTO ou DUAS_COLUNAS
 */
public record ResumeLintWarning(String code, String message) {
}
