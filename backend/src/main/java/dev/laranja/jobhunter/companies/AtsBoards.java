package dev.laranja.jobhunter.companies;

import java.util.Optional;

/**
 * Job boards públicos que a coleta sabe consultar. Implementado pelo módulo de coleta, que conhece as URLs e as APIs
 * de cada ATS; o cadastro de empresas usa para preencher o ATS a partir da página de carreira e conferir o slug.
 */
public interface AtsBoards {

	/** ATS e slug reconhecidos no link da página de carreira; vazio quando o link não é de um ATS suportado. */
	Optional<AtsBoard> detect(String careersUrl);

	/** Se o ATS tem coleta automática (os demais, como Gupy, ficam só anotados). */
	boolean supports(String atsType);

	/** Confere no ATS se o slug existe. ATS sem coleta automática devolve {@link AtsSlugStatus#NAO_CONFIRMADO}. */
	AtsSlugStatus verify(String atsType, String slug);

}
