package dev.laranja.jobhunter.companies;

/** Uma empresa nova apareceu numa vaga. Classificação de nicho e busca do selo GPTW escutam este evento. */
public record CompanyDiscovered(long companyId, String name) {
}
