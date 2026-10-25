package dev.laranja.jobhunter.tailoring;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import dev.laranja.jobhunter.analysis.JobAnalyzer;
import dev.laranja.jobhunter.analysis.ResumeFit;
import dev.laranja.jobhunter.analysis.ResumeFit.JobAnalysisView;
import dev.laranja.jobhunter.analysis.ResumeFit.TextFit;
import dev.laranja.jobhunter.jobs.JobCatalog;
import dev.laranja.jobhunter.jobs.JobView;
import dev.laranja.jobhunter.resumes.GeneratedResumes;
import dev.laranja.jobhunter.resumes.ResumeCatalog;
import dev.laranja.jobhunter.resumes.ResumeCatalog.ResumeProfile;
import dev.laranja.jobhunter.resumes.ResumeRef;
import dev.laranja.jobhunter.shared.BusinessRuleException;
import dev.laranja.jobhunter.shared.ConflictException;
import dev.laranja.jobhunter.shared.NotFoundException;
import dev.laranja.jobhunter.shared.TextNormalizer;
import dev.laranja.jobhunter.shared.jdbc.JdbcRows;
import dev.laranja.jobhunter.tailoring.TailoredResumeView.ResumeChange;
import dev.laranja.jobhunter.tailoring.internal.MarkdownDocx;
import dev.laranja.jobhunter.tailoring.internal.ResumeTailor;
import dev.laranja.jobhunter.tailoring.internal.TailoredDraft;
import dev.laranja.jobhunter.tailoring.internal.TailoringRequested;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.json.JsonMapper;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Currículos adaptados por vaga. O pedido grava a versão como GERANDO e a reescrita roda depois, fora da
 * requisição; o resultado traz a cobertura das palavras-chave da vaga e o score antes e depois, calculados pelas
 * mesmas regras da análise, e as skills que apareceram no texto sem estar no currículo base.
 */
@Service
@Transactional
@EnableConfigurationProperties(TailoringProperties.class)
public class TailoredResumes {

	static final int MAX_CONTENT = 50_000;

	/** Uma geração parada há mais que isso foi interrompida (o processo caiu no meio da chamada). */
	static final Duration STALE_GENERATION = Duration.ofMinutes(30);

	private static final Logger log = LoggerFactory.getLogger(TailoredResumes.class);

	private static final String SELECT = """
			select t.id, t.job_id, t.base_resume_version_id, b.label as base_label, t.status, t.content, t.headline,
			       t.changes::text as changes, t.not_covered::text as not_covered, t.notes, t.keywords::text as keywords,
			       t.covered_before::text as covered_before, t.covered_after::text as covered_after,
			       t.unsupported_skills::text as unsupported, t.score_before, t.score_after, t.edited,
			       t.resume_version_id, r.label as resume_label, t.model_version, t.last_error, t.created_at, t.updated_at
			from tailored_resume t
			left join resume_version b on b.id = t.base_resume_version_id
			left join resume_version r on r.id = t.resume_version_id
			""";

	private final JobCatalog jobs;

	private final ResumeCatalog resumes;

	private final GeneratedResumes generated;

	private final JobAnalyzer analyzer;

	private final ResumeFit fit;

	private final ResumeTailor tailor;

	private final MarkdownDocx docx;

	private final JdbcClient jdbc;

	private final JsonMapper json;

	private final ApplicationEventPublisher events;

	private final TailoringProperties properties;

	private final TransactionTemplate transactions;

	private final Clock clock;

	TailoredResumes(JobCatalog jobs, ResumeCatalog resumes, GeneratedResumes generated, JobAnalyzer analyzer,
			ResumeFit fit, ResumeTailor tailor, MarkdownDocx docx, JdbcClient jdbc, JsonMapper json,
			ApplicationEventPublisher events, TailoringProperties properties,
			PlatformTransactionManager transactionManager, Clock clock) {
		this.jobs = jobs;
		this.resumes = resumes;
		this.generated = generated;
		this.analyzer = analyzer;
		this.fit = fit;
		this.tailor = tailor;
		this.docx = docx;
		this.jdbc = jdbc;
		this.json = json;
		this.events = events;
		this.properties = properties;
		this.transactions = new TransactionTemplate(transactionManager);
		this.clock = clock;
	}

	/**
	 * Pede uma versão adaptada para a vaga, a partir do currículo informado ou do padrão.
	 * @return a versão recém-criada, ainda em GERANDO
	 */
	public TailoredResumeView request(long jobId, @Nullable Long baseResumeId) {
		if (!this.tailor.enabled()) {
			throw new BusinessRuleException(
					"A adaptação do currículo usa a Claude API: configure JOBHUNTER_AI_API_KEY para habilitar.");
		}
		this.jobs.get(jobId);
		ResumeProfile base = ((baseResumeId != null) ? this.resumes.find(baseResumeId) : this.resumes.defaultResume())
			.orElseThrow(() -> (baseResumeId != null) ? NotFoundException.of("Currículo", baseResumeId)
					: new BusinessRuleException("Envie um currículo antes de adaptá-lo para uma vaga."));
		if (base.text() == null || base.text().isBlank()) {
			throw new BusinessRuleException("O currículo \"" + base.label()
					+ "\" não tem texto legível (PDF escaneado?). Envie uma versão com texto.");
		}
		Instant now = this.clock.instant();
		this.jdbc.sql("""
				update tailored_resume set status = 'FALHOU', last_error = 'A geração foi interrompida.', updated_at = :now
				where job_id = :jobId and status = 'GERANDO' and updated_at < :staleBefore
				""")
			.param("jobId", jobId)
			.param("now", Timestamp.from(now))
			.param("staleBefore", Timestamp.from(now.minus(STALE_GENERATION)))
			.update();
		this.jdbc.sql("select id from tailored_resume where job_id = :jobId and status = 'GERANDO'")
			.param("jobId", jobId)
			.query(Long.class)
			.optional()
			.ifPresent(existing -> {
				throw new ConflictException("Já existe uma versão sendo gerada para esta vaga.", existing);
			});
		long id = this.jdbc.sql("""
				insert into tailored_resume (job_id, base_resume_version_id, status, created_at, updated_at)
				values (:jobId, :baseId, 'GERANDO', :now, :now)
				returning id
				""")
			.param("jobId", jobId)
			.param("baseId", base.id())
			.param("now", Timestamp.from(now))
			.query(Long.class)
			.single();
		this.events.publishEvent(new TailoringRequested(id));
		return get(id);
	}

	/**
	 * Reescreve o currículo da versão pedida. Falhas ficam registradas na própria versão (FALHOU). Roda fora de
	 * transação para não segurar uma conexão durante a chamada ao LLM; só a gravação do resultado é transacional.
	 */
	@Transactional(propagation = Propagation.NOT_SUPPORTED)
	public void generate(long id) {
		TailoredResumeView pending = get(id);
		if (!"GERANDO".equals(pending.status())) {
			return;
		}
		try {
			JobView job = this.jobs.get(pending.jobId());
			ResumeProfile base = Optional.ofNullable(pending.baseResumeId())
				.flatMap(this.resumes::find)
				.orElseThrow(() -> new BusinessRuleException("O currículo base foi removido."));
			JobAnalysisView analysis = analysisOf(job.id());
			TailoredDraft draft = this.tailor.rewrite(job, analysis, base);
			Fits fits = fits(analysis, base.text(), draft.markdown(), job.title());
			this.transactions.executeWithoutResult(status -> saveDraft(id, job.id(), draft, fits));
		}
		catch (RuntimeException ex) {
			log.warn("Não foi possível adaptar o currículo (versão {}): {}", id, ex.getMessage());
			this.jdbc.sql("update tailored_resume set status = 'FALHOU', last_error = :error, updated_at = :now where id = :id")
				.param("id", id)
				.param("error", (ex.getMessage() != null) ? ex.getMessage() : ex.getClass().getSimpleName())
				.param("now", Timestamp.from(this.clock.instant()))
				.update();
		}
	}

	private void saveDraft(long id, long jobId, TailoredDraft draft, Fits fits) {
		this.jdbc.sql("""
				update tailored_resume set status = 'PRONTO', content = :content, headline = :headline,
				    changes = cast(:changes as jsonb), not_covered = cast(:notCovered as jsonb), notes = :notes,
				    keywords = cast(:keywords as jsonb), covered_before = cast(:coveredBefore as jsonb),
				    covered_after = cast(:coveredAfter as jsonb), unsupported_skills = cast(:unsupported as jsonb),
				    score_before = :scoreBefore, score_after = :scoreAfter, model_version = :modelVersion,
				    last_error = null, updated_at = :now
				where id = :id
				""")
			.param("id", id)
			.param("content", draft.markdown().strip())
			.param("headline", truncate(draft.headline(), 300))
			.param("changes", this.json.writeValueAsString(orEmpty(draft.changes())))
			.param("notCovered", this.json.writeValueAsString(orEmpty(draft.notCovered())))
			.param("notes", draft.notes())
			.param("keywords", this.json.writeValueAsString(fits.after().keywords()))
			.param("coveredBefore", this.json.writeValueAsString(fits.before().covered()))
			.param("coveredAfter", this.json.writeValueAsString(fits.after().covered()))
			.param("unsupported", this.json.writeValueAsString(fits.unsupported()))
			.param("scoreBefore", fits.before().score())
			.param("scoreAfter", fits.after().score())
			.param("modelVersion", this.tailor.version())
			.param("now", Timestamp.from(this.clock.instant()))
			.update();
		this.events.publishEvent(new TailoredResumeReady(id, jobId, fits.after().covered().size(),
				fits.after().keywords().size()));
	}

	/**
	 * Troca o texto por uma edição manual e recalcula a cobertura. Se o texto anterior já estava salvo em
	 * Currículos, a versão salva continua lá, e o texto novo pode ser salvo como outra versão.
	 */
	public TailoredResumeView edit(long id, String content) {
		TailoredResumeView current = get(id);
		if (!"PRONTO".equals(current.status())) {
			throw new BusinessRuleException("Só dá para editar uma versão pronta.");
		}
		if (content == null || content.isBlank()) {
			throw new BusinessRuleException("O currículo não pode ficar vazio.");
		}
		if (content.length() > MAX_CONTENT) {
			throw new BusinessRuleException("O currículo deve ter no máximo " + MAX_CONTENT + " caracteres.");
		}
		JobView job = this.jobs.get(current.jobId());
		String baseText = Optional.ofNullable(current.baseResumeId())
			.flatMap(this.resumes::find)
			.map(ResumeProfile::text)
			.orElse("");
		Fits fits = fits(analysisOf(job.id()), baseText, content, job.title());
		this.jdbc.sql("""
				update tailored_resume set content = :content, edited = true, resume_version_id = null,
				    keywords = cast(:keywords as jsonb), covered_before = cast(:coveredBefore as jsonb),
				    covered_after = cast(:coveredAfter as jsonb), unsupported_skills = cast(:unsupported as jsonb),
				    score_before = :scoreBefore, score_after = :scoreAfter, updated_at = :now
				where id = :id
				""")
			.param("id", id)
			.param("content", content.strip())
			.param("keywords", this.json.writeValueAsString(fits.after().keywords()))
			.param("coveredBefore", this.json.writeValueAsString(fits.before().covered()))
			.param("coveredAfter", this.json.writeValueAsString(fits.after().covered()))
			.param("unsupported", this.json.writeValueAsString(fits.unsupported()))
			.param("scoreBefore", fits.before().score())
			.param("scoreAfter", fits.after().score())
			.param("now", Timestamp.from(this.clock.instant()))
			.update();
		return get(id);
	}

	/**
	 * Gera o DOCX e o registra como uma versão do currículo, para escolher na candidatura e medir a conversão.
	 * @return a versão do currículo criada
	 */
	public ResumeRef saveAsResumeVersion(long id) {
		TailoredResumeView current = get(id);
		if (!"PRONTO".equals(current.status()) || current.content() == null) {
			throw new BusinessRuleException("Só dá para salvar uma versão pronta.");
		}
		if (current.resumeVersionId() != null) {
			throw new ConflictException("Este texto já está salvo em Currículos como \"" + current.resumeLabel() + "\".",
					current.resumeVersionId());
		}
		JobView job = this.jobs.get(current.jobId());
		String label = truncate(job.title() + " · " + job.company(), 120);
		ResumeRef saved = this.generated.register(this.docx.write(current.content(), label), fileName(job), label,
				truncate(job.title(), 120));
		this.jdbc.sql("update tailored_resume set resume_version_id = :resumeId, updated_at = :now where id = :id")
			.param("id", id)
			.param("resumeId", saved.id())
			.param("now", Timestamp.from(this.clock.instant()))
			.update();
		return saved;
	}

	/** Se a instalação consegue adaptar currículos (tem a chave da Claude API) e se gera ao marcar interesse. */
	@Transactional(readOnly = true)
	public Availability availability() {
		boolean enabled = this.tailor.enabled();
		return new Availability(enabled, enabled && this.properties.autoGenerate());
	}

	@Transactional(readOnly = true)
	public Document document(long id) {
		TailoredResumeView current = get(id);
		if (current.content() == null) {
			throw new BusinessRuleException("Esta versão ainda não tem texto.");
		}
		JobView job = this.jobs.get(current.jobId());
		return new Document(fileName(job), this.docx.write(current.content(), job.title() + " · " + job.company()));
	}

	@Transactional(readOnly = true)
	public List<TailoredResumeView> forJob(long jobId) {
		return withCoverage(jobId, this.jdbc.sql(SELECT + " where t.job_id = :jobId order by t.created_at desc, t.id desc")
			.param("jobId", jobId)
			.query((rs, row) -> toView(rs))
			.list());
	}

	@Transactional(readOnly = true)
	public boolean existsForJob(long jobId) {
		return this.jdbc.sql("select exists (select 1 from tailored_resume where job_id = :jobId)")
			.param("jobId", jobId)
			.query(Boolean.class)
			.single();
	}

	@Transactional(readOnly = true)
	public TailoredResumeView get(long id) {
		TailoredResumeView view = this.jdbc.sql(SELECT + " where t.id = :id")
			.param("id", id)
			.query((rs, row) -> toView(rs))
			.optional()
			.orElseThrow(() -> NotFoundException.of("Currículo adaptado", id));
		return withCoverage(view.jobId(), List.of(view)).get(0);
	}

	/** A análise da vaga; se ela ainda não rodou (vaga recém-criada), roda agora. */
	private JobAnalysisView analysisOf(long jobId) {
		return this.fit.analysis(jobId).or(() -> {
			this.analyzer.analyze(jobId);
			return this.fit.analysis(jobId);
		}).orElseThrow(() -> new BusinessRuleException("A vaga ainda não foi analisada."));
	}

	private Fits fits(JobAnalysisView analysis, @Nullable String baseText, String tailoredText, String targetRole) {
		String base = (baseText != null) ? baseText : "";
		TextFit before = this.fit.evaluate(analysis, base, targetRole);
		TextFit after = this.fit.evaluate(analysis, tailoredText, targetRole);
		Set<String> baseSkills = this.fit.skillsIn(base)
			.stream()
			.map(skill -> skill.toLowerCase(Locale.ROOT))
			.collect(Collectors.toSet());
		List<String> unsupported = this.fit.skillsIn(tailoredText)
			.stream()
			.filter(skill -> !baseSkills.contains(skill.toLowerCase(Locale.ROOT)))
			.toList();
		return new Fits(before, after, unsupported);
	}

	private TailoredResumeView toView(ResultSet rs) throws SQLException {
		return new TailoredResumeView(rs.getLong("id"), rs.getLong("job_id"),
				JdbcRows.nullableLong(rs, "base_resume_version_id"), rs.getString("base_label"), rs.getString("status"),
				rs.getString("content"), rs.getString("headline"),
				List.of(this.json.readValue(rs.getString("changes"), ResumeChange[].class)),
				readList(rs.getString("not_covered")), rs.getString("notes"), readList(rs.getString("keywords")),
				readList(rs.getString("covered_before")), readList(rs.getString("covered_after")),
				readList(rs.getString("unsupported")), (Integer) rs.getObject("score_before"),
				(Integer) rs.getObject("score_after"), rs.getBoolean("edited"),
				JdbcRows.nullableLong(rs, "resume_version_id"), rs.getString("resume_label"),
				rs.getString("model_version"), rs.getString("last_error"), JdbcRows.instant(rs, "created_at"),
				JdbcRows.instant(rs, "updated_at"), null);
	}

	/** Calcula a cobertura do texto adaptado na leitura, com a análise atual da vaga. */
	private List<TailoredResumeView> withCoverage(long jobId, List<TailoredResumeView> views) {
		if (views.stream().allMatch(view -> view.content() == null)) {
			return views;
		}
		Optional<JobAnalysisView> analysis = this.fit.analysis(jobId);
		if (analysis.isEmpty()) {
			return views;
		}
		String title = this.jobs.get(jobId).title();
		return views.stream()
			.map(view -> (view.content() == null) ? view
					: view.withAts(this.fit.coverage(analysis.get(), title, view.content(), view.headline())))
			.toList();
	}

	private List<String> readList(String value) {
		return List.of(this.json.readValue(value, String[].class));
	}

	/** Nome do arquivo sem acentos nem espaços, que qualquer ATS aceita. */
	static String fileName(JobView job) {
		String slug = TextNormalizer.normalize(job.company() + " " + job.title()).replace(' ', '-');
		return "curriculo-" + ((slug.length() > 80) ? slug.substring(0, 80) : slug) + ".docx";
	}

	private static <T> List<T> orEmpty(@Nullable List<T> values) {
		return (values == null) ? List.of() : values;
	}

	private static @Nullable String truncate(@Nullable String value, int max) {
		if (value == null) {
			return null;
		}
		String stripped = value.strip();
		return (stripped.length() <= max) ? stripped : stripped.substring(0, max);
	}

	/**
	 * @param enabled a Claude API está configurada
	 * @param autoGenerate a primeira versão é gerada ao marcar interesse na vaga
	 */
	public record Availability(boolean enabled, boolean autoGenerate) {
	}

	/** O DOCX de uma versão, para download. */
	public record Document(String fileName, byte[] content) {
	}

	private record Fits(TextFit before, TextFit after, List<String> unsupported) {
	}

}
