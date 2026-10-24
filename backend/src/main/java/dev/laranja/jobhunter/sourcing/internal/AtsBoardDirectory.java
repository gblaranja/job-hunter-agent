package dev.laranja.jobhunter.sourcing.internal;

import java.util.Locale;
import java.util.Optional;

import dev.laranja.jobhunter.companies.AtsBoard;
import dev.laranja.jobhunter.companies.AtsBoards;
import dev.laranja.jobhunter.companies.AtsSlugStatus;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/** Os ATS com coleta automática, para o cadastro de empresas detectar o ATS pelo link e conferir o slug. */
@Component
class AtsBoardDirectory implements AtsBoards {

	/** Resolvido sob demanda: as fontes dependem do catálogo de vagas, que depende do cadastro de empresas. */
	private final ObjectProvider<AtsSource> sources;

	AtsBoardDirectory(ObjectProvider<AtsSource> sources) {
		this.sources = sources;
	}

	@Override
	public Optional<AtsBoard> detect(String careersUrl) {
		return AtsLinks.detect(careersUrl);
	}

	@Override
	public boolean supports(String atsType) {
		return source(atsType).isPresent();
	}

	@Override
	public AtsSlugStatus verify(String atsType, String slug) {
		return source(atsType).map(source -> source.verify(slug.strip())).orElse(AtsSlugStatus.NAO_CONFIRMADO);
	}

	private Optional<AtsSource> source(String atsType) {
		String name = atsType.strip().toUpperCase(Locale.ROOT);
		return this.sources.orderedStream().filter(source -> source.origin().name().equals(name)).findFirst();
	}

}
