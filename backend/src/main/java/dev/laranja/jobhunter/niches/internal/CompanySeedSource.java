package dev.laranja.jobhunter.niches.internal;

import java.util.function.Consumer;

import dev.laranja.jobhunter.companies.CompanyKind;
import org.jspecify.annotations.Nullable;

/**
 * Fonte proativa de empresas para os nichos (Banco Central, Receita). Um nicho novo com uma base própria é só mais
 * um adapter desta interface.
 */
public interface CompanySeedSource {

	/** Identificador usado em {@code POST /api/admin/seeds/{source}/run}. */
	String key();

	String label();

	/** Motivo de a fonte estar desligada; nulo quando está pronta para rodar. */
	@Nullable String unavailableReason();

	/** Entrega as empresas uma a uma, para as bases grandes serem lidas em streaming. */
	void fetch(Consumer<SeedRecord> sink);

	/**
	 * @param cnpj raiz do CNPJ (8 dígitos)
	 * @param authorizationType tipo de autorização no Banco Central ou "CNAE 6204-0/00"; casa com
	 * {@code niche_segment.authorization_types}
	 */
	record SeedRecord(String name, String cnpj, String authorizationType, CompanyKind kind, @Nullable String website,
			@Nullable String location) {
	}

}
