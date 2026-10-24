package dev.laranja.jobhunter.companies;

/** Resultado do cadastro vindo de uma base pública: a empresa e se ela é nova. */
public record SeededCompany(long companyId, boolean created) {
}
