package dev.laranja.jobhunter.companies;

/**
 * Job board de uma empresa num ATS.
 *
 * @param atsType nome do ATS, igual ao da origem das vagas (por exemplo, {@code LEVER})
 * @param slug identificador da empresa no ATS (o trecho da URL do job board)
 */
public record AtsBoard(String atsType, String slug) {
}
