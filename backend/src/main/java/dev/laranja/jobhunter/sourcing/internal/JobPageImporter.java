package dev.laranja.jobhunter.sourcing.internal;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import dev.laranja.jobhunter.jobs.WorkModel;
import dev.laranja.jobhunter.shared.ai.AiProperties;
import dev.laranja.jobhunter.shared.ai.LlmClient;
import dev.laranja.jobhunter.shared.ai.LlmClient.Effort;
import dev.laranja.jobhunter.shared.ai.LlmClient.LlmRequest;
import dev.laranja.jobhunter.shared.ai.LlmException;
import dev.laranja.jobhunter.sourcing.internal.JobPostingJsonLd.Posting;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.json.JsonMapper;

import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * Importa uma vaga por link: lê o {@code JobPosting} em JSON-LD da página (o que os ATS e sites de carreira publicam)
 * ou, sem ele, extrai do texto com o Claude Haiku ({@code extract-posting.v1}). O conteúdo pode vir já capturado pelo
 * navegador (extensão), e aí a página não é baixada. Links do LinkedIn só com o conteúdo capturado: o servidor não
 * busca páginas do LinkedIn (cláusula 8.2 dos termos, decisão D-01).
 */
@Component
public class JobPageImporter {

	static final String PROMPT_ID = "extract-posting@v1";

	/** Texto mandado ao Claude: o suficiente para uma vaga, sem pagar por rodapés enormes. */
	private static final int MAX_TEXT = 30_000;

	private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy")
		.withZone(ZoneId.of("America/Sao_Paulo"));

	private final PageFetcher fetcher;

	private final JsonMapper json;

	private final LlmClient llm;

	private final AiProperties ai;

	private final JdbcClient jdbc;

	private final Clock clock;

	private final String prompt;

	JobPageImporter(PageFetcher fetcher, JsonMapper json, LlmClient llm, AiProperties ai, JdbcClient jdbc, Clock clock) {
		this.fetcher = fetcher;
		this.json = json;
		this.llm = llm;
		this.ai = ai;
		this.jdbc = jdbc;
		this.clock = clock;
		try {
			this.prompt = new ClassPathResource("prompts/extract-posting.v1.md").getContentAsString(StandardCharsets.UTF_8)
				.strip();
		}
		catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
	}

	/**
	 * @param jsonLd conteúdo dos blocos {@code application/ld+json} já capturado pelo navegador
	 * @param text texto da vaga já capturado (selecionado) pelo navegador
	 */
	public JobImportPreview preview(String url, @Nullable String jsonLd, @Nullable String text) {
		URI uri = this.fetcher.validateSyntax(url);
		boolean captured = notBlank(jsonLd) || notBlank(text);
		if (!captured && isLinkedIn(uri)) {
			throw PageFetcher.badRequest("O Job Hunter não baixa páginas do LinkedIn (os termos de uso proíbem robôs). "
					+ "Cole a descrição no formulário ou use a extensão do navegador, que envia o que você está vendo.");
		}
		if (notBlank(jsonLd)) {
			Optional<Posting> posting = JobPostingJsonLd.parse(this.json, List.of(jsonLd));
			if (posting.isPresent()) {
				return fromPosting(url, posting.get());
			}
		}
		if (notBlank(text)) {
			return fromText(url, text, null, null);
		}
		if (captured) {
			throw PageFetcher.unprocessable("O conteúdo enviado não tem os dados de uma vaga.");
		}
		Document page = Jsoup.parse(this.fetcher.fetch(url), url);
		List<String> blocks = page.select("script[type=application/ld+json]").stream().map(Element::data).toList();
		Optional<Posting> posting = JobPostingJsonLd.parse(this.json, blocks);
		if (posting.isPresent()) {
			return fromPosting(url, posting.get());
		}
		String mainText = mainText(page);
		if (mainText == null || mainText.length() < 200) {
			throw PageFetcher.unprocessable("Não encontrei os dados de uma vaga nessa página. Cole a descrição à mão.");
		}
		return fromText(url, mainText, meta(page, "og:title", page.title()), meta(page, "og:site_name", null));
	}

	private JobImportPreview fromPosting(String url, Posting posting) {
		List<String> warnings = new ArrayList<>();
		if (posting.validThrough() != null && posting.validThrough().isBefore(this.clock.instant())) {
			warnings.add("As inscrições terminaram em " + DATE.format(posting.validThrough()) + ".");
		}
		missing(posting.title(), "o cargo", warnings);
		missing(posting.company(), "a empresa", warnings);
		missing(posting.description(), "a descrição", warnings);
		return preview(url, posting.title(), posting.company(), posting.location(), posting.workModel(),
				posting.salaryRange(), posting.description(), "JSON_LD", warnings);
	}

	private JobImportPreview fromText(String url, String text, @Nullable String pageTitle, @Nullable String siteName) {
		String clipped = (text.length() <= MAX_TEXT) ? text.strip() : text.substring(0, MAX_TEXT);
		List<String> warnings = new ArrayList<>();
		if (this.llm.enabled()) {
			try {
				Extracted extracted = this.llm.structured(new LlmRequest("extract-posting", this.ai.models().extraction(),
						this.prompt, "Link: " + url + "\n\n" + clipped, 4000, Effort.LOW), Extracted.class);
				warnings.add("Dados extraídos do texto da página pelo Claude: confira antes de salvar.");
				missing(extracted.title(), "o cargo", warnings);
				missing(extracted.company(), "a empresa", warnings);
				return preview(url, blank(extracted.title()), blank(extracted.company()), blank(extracted.location()),
						workModel(extracted.workModel()), blank(extracted.salaryRange()),
						notBlank(extracted.description()) ? extracted.description().strip() : clipped, "IA", warnings);
			}
			catch (LlmException ex) {
				warnings.add("O Claude não respondeu (" + ex.getMessage() + "); veio só o texto da página.");
			}
		}
		else {
			warnings.add("A página não tem dados estruturados e a IA está desligada: preencha cargo e empresa.");
		}
		return preview(url, blank(pageTitle), blank(siteName), null, null, null, clipped, "TEXTO", warnings);
	}

	private JobImportPreview preview(String url, @Nullable String title, @Nullable String company,
			@Nullable String location, @Nullable WorkModel workModel, @Nullable String salaryRange,
			@Nullable String description, String method, List<String> warnings) {
		Long existing = existingJob(url, title, company);
		if (existing != null) {
			warnings.add("Essa vaga já está cadastrada.");
		}
		return new JobImportPreview(cut(title, 300), cut(company, 200), cut(location, 200), workModel, null,
				cut(salaryRange, 100), url, description, method, List.copyOf(warnings), existing);
	}

	/** Mesmo link ou, aberta, mesma empresa e mesmo cargo. */
	private @Nullable Long existingJob(String url, @Nullable String title, @Nullable String company) {
		return this.jdbc.sql("""
				select id from job
				where url = :url
				   or (cast(:title as text) is not null and cast(:company as text) is not null and closed_at is null
				       and lower(company) = lower(:company) and lower(title) = lower(:title))
				order by duplicate_of_job_id is null desc, id
				limit 1
				""")
			.param("url", url)
			.param("title", title)
			.param("company", company)
			.query(Long.class)
			.optional()
			.orElse(null);
	}

	private static @Nullable String mainText(Document page) {
		page.select("script, style, noscript, nav, header, footer, form, svg, iframe").remove();
		Element main = page.selectFirst("main, article, [role=main]");
		Element root = (main != null) ? main : page.body();
		return (root == null) ? null : HtmlText.toText(root.html(), false);
	}

	private static @Nullable String meta(Document page, String property, @Nullable String fallback) {
		Element meta = page.selectFirst("meta[property=" + property + "]");
		String content = (meta != null) ? meta.attr("content") : null;
		return notBlank(content) ? content.strip() : fallback;
	}

	static boolean isLinkedIn(URI uri) {
		String host = (uri.getHost() == null) ? "" : uri.getHost().toLowerCase(Locale.ROOT);
		return host.equals("linkedin.com") || host.endsWith(".linkedin.com") || host.equals("lnkd.in");
	}

	private static @Nullable WorkModel workModel(@Nullable String value) {
		if (value == null) {
			return null;
		}
		try {
			WorkModel model = WorkModel.valueOf(value.strip().toUpperCase(Locale.ROOT));
			return (model == WorkModel.NAO_INFORMADO) ? null : model;
		}
		catch (IllegalArgumentException ex) {
			return null;
		}
	}

	private static void missing(@Nullable String value, String what, List<String> warnings) {
		if (!notBlank(value)) {
			warnings.add("A página não informa " + what + "; preencha à mão.");
		}
	}

	private static boolean notBlank(@Nullable String value) {
		return value != null && !value.isBlank();
	}

	private static @Nullable String blank(@Nullable String value) {
		return notBlank(value) ? value.strip() : null;
	}

	private static @Nullable String cut(@Nullable String value, int max) {
		if (value == null) {
			return null;
		}
		return (value.length() <= max) ? value : value.substring(0, max).strip();
	}

	record Extracted(@JsonPropertyDescription("Cargo anunciado; vazio se não houver") String title,
			@JsonPropertyDescription("Empresa contratante; vazio se não houver") String company,
			@JsonPropertyDescription("Cidade e UF, país ou Remoto; vazio se não houver") String location,
			@JsonPropertyDescription("REMOTO, HIBRIDO, PRESENCIAL ou NAO_INFORMADO") String workModel,
			@JsonPropertyDescription("Faixa salarial como escrita; vazio se não houver") String salaryRange,
			@JsonPropertyDescription("Descrição da vaga em texto, com listas em \"- \"") String description) {
	}

}
