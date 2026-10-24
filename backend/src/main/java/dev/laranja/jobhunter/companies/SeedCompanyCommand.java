package dev.laranja.jobhunter.companies;

import org.jspecify.annotations.Nullable;

/**
 * Empresa de uma base pública.
 *
 * @param cnpj raiz do CNPJ (8 dígitos)
 * @param authorizationType tipo de autorização no Banco Central ou CNAE principal na Receita
 */
public record SeedCompanyCommand(String name, String cnpj, String authorizationType, CompanyKind kind,
		@Nullable String website, @Nullable String location) {
}
