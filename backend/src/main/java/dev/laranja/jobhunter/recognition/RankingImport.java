package dev.laranja.jobhunter.recognition;

import java.sql.Date;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import dev.laranja.jobhunter.recognition.internal.CompanyMatcher;
import dev.laranja.jobhunter.recognition.internal.CompanyMatcher.Match;
import dev.laranja.jobhunter.recognition.internal.CompanyMatcher.MatchKind;
import dev.laranja.jobhunter.recognition.internal.PageText;
import dev.laranja.jobhunter.recognition.internal.RankingListExtractor;
import dev.laranja.jobhunter.shared.BusinessRuleException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.jspecify.annotations.Nullable;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Importação em lote de uma lista de premiadas (GPTW TI do ano, por exemplo): o LLM extrai os nomes, os nomes são
 * casados com as empresas cadastradas (nome, aliases e trigramas) e você confirma a prévia.
 */
@Service
public class RankingImport {

	private static final ZoneId ZONE = ZoneId.of("America/Sao_Paulo");

	private final RankingListExtractor extractor;

	private final CompanyMatcher matcher;

	private final PageText pages;

	private final JdbcClient jdbc;

	private final ApplicationEventPublisher events;

	private final Clock clock;

	RankingImport(RankingListExtractor extractor, CompanyMatcher matcher, PageText pages, JdbcClient jdbc,
			ApplicationEventPublisher events, Clock clock) {
		this.extractor = extractor;
		this.matcher = matcher;
		this.pages = pages;
		this.jdbc = jdbc;
		this.events = events;
		this.clock = clock;
	}

	/** Prévia: cada nome da lista com a empresa cadastrada correspondente, quando houver. Nada é gravado. */
	@Transactional(readOnly = true)
	public ImportPreview preview(ImportSource source) {
		String text = (source.text() != null && !source.text().isBlank()) ? source.text()
				: (source.url() != null && !source.url().isBlank()) ? this.pages.fetch(source.url()) : null;
		if (text == null) {
			throw new BusinessRuleException("Cole o texto da lista ou informe o link.");
		}
		List<ImportItem> items = this.extractor.extract(text).stream().map(entry -> {
			Match match = this.matcher.match(entry.name());
			return new ImportItem(entry.name(), entry.position(), match.companyId(), match.companyName(), match.kind(),
					Math.round(match.similarity() * 100) / 100.0);
		}).toList();
		return new ImportPreview(items, (int) items.stream().filter(item -> item.companyId() != null).count());
	}

	/** Grava o selo, confirmado por você, para as empresas escolhidas na prévia. */
	@Transactional
	public int confirm(ImportConfirmation confirmation) {
		if (confirmation.level() == RecognitionLevel.EXPIRADA || confirmation.level() == RecognitionLevel.NAO_ENCONTRADA) {
			throw new BusinessRuleException("Escolha ranking GPTW TI, outro ranking ou certificação.");
		}
		LocalDate today = LocalDate.now(this.clock.withZone(ZONE));
		RecognitionLevel level = (confirmation.level() == RecognitionLevel.RANKING_TI
				&& confirmation.year() < today.getYear() - 1) ? RecognitionLevel.RANKING : confirmation.level();
		Set<Long> companies = new LinkedHashSet<>();
		for (ConfirmedItem item : confirmation.items()) {
			if (!companies.add(item.companyId())) {
				continue;
			}
			int updated = this.jdbc.sql("""
					update company_recognition set review_status = 'CONFIRMADO', verified_by = 'USUARIO',
					    ranking_position = coalesce(:position, ranking_position), checked_at = :now
					where company_id = :companyId and program = 'GPTW' and level = :level and year = :year
					  and ranking_name is not distinct from :rankingName and review_status <> 'REJEITADO'
					""")
				.param("companyId", item.companyId())
				.param("level", level.name())
				.param("year", confirmation.year())
				.param("rankingName", blankToNull(confirmation.rankingName()), Types.VARCHAR)
				.param("position", item.position(), Types.INTEGER)
				.param("now", Timestamp.from(this.clock.instant()))
				.update();
			if (updated == 0) {
				this.jdbc.sql("""
						insert into company_recognition (company_id, program, level, ranking_name, ranking_position, year,
						    valid_until, evidence_url, confidence, verified_by, review_status, source, checked_at)
						values (:companyId, 'GPTW', :level, :rankingName, :position, :year, :validUntil, :evidenceUrl, 1,
						        'USUARIO', 'CONFIRMADO', 'IMPORTACAO', :now)
						""")
					.param("companyId", item.companyId())
					.param("level", level.name())
					.param("rankingName", blankToNull(confirmation.rankingName()), Types.VARCHAR)
					.param("position", item.position(), Types.INTEGER)
					.param("year", confirmation.year())
					.param("validUntil", Date.valueOf(LocalDate.of(confirmation.year() + 1, 12, 31)))
					.param("evidenceUrl", blankToNull(confirmation.evidenceUrl()), Types.VARCHAR)
					.param("now", Timestamp.from(this.clock.instant()))
					.update();
			}
		}
		companies.forEach(companyId -> this.events.publishEvent(new RecognitionChanged(companyId)));
		return companies.size();
	}

	private static @Nullable String blankToNull(@Nullable String value) {
		return (value == null || value.isBlank()) ? null : value.strip();
	}

	/** O texto colado ou o link da página com a lista. */
	public record ImportSource(@Nullable String text, @Size(max = 1000) @Nullable String url) {
	}

	/**
	 * @param match EXATO (nome ou alias), SIMILAR (trigramas, confira) ou NENHUM
	 */
	public record ImportItem(String extractedName, @Nullable Integer position, @Nullable Long companyId,
			@Nullable String companyName, MatchKind match, double similarity) {
	}

	public record ImportPreview(List<ImportItem> items, int matched) {
	}

	public record ImportConfirmation(@NotNull RecognitionLevel level, @NotNull @Min(2000) @Max(2100) Integer year,
			@Size(max = 200) @Nullable String rankingName, @Size(max = 1000) @Nullable String evidenceUrl,
			@NotEmpty List<@Valid ConfirmedItem> items) {
	}

	public record ConfirmedItem(@NotNull Long companyId, @Nullable Integer position) {
	}

}
