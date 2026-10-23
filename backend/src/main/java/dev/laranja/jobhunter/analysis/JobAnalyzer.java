package dev.laranja.jobhunter.analysis;

import java.sql.Array;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import dev.laranja.jobhunter.analysis.internal.AnalysisEngine;
import dev.laranja.jobhunter.analysis.internal.AnalysisEngine.Outcome;
import dev.laranja.jobhunter.jobs.JobCatalog;
import dev.laranja.jobhunter.jobs.JobView;
import dev.laranja.jobhunter.resumes.ResumeCatalog;
import dev.laranja.jobhunter.resumes.ResumeCatalog.ResumeProfile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.json.JsonMapper;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Analisa as vagas e mantém o score de aderência em dia. A camada determinística roda sempre; o LLM, quando
 * configurado, refina a extração e ajusta o score. Se o LLM falhar, a análise determinística fica salva e a etapa
 * do LLM é tentada de novo depois (até {@value #MAX_ATTEMPTS} vezes, com espera crescente).
 */
@Service
public class JobAnalyzer {

	static final int MAX_ATTEMPTS = 5;

	private static final Logger log = LoggerFactory.getLogger(JobAnalyzer.class);

	private final JobCatalog jobs;

	private final ResumeCatalog resumes;

	private final AnalysisEngine engine;

	private final JdbcClient jdbc;

	private final JsonMapper json;

	private final ApplicationEventPublisher events;

	private final Clock clock;

	JobAnalyzer(JobCatalog jobs, ResumeCatalog resumes, AnalysisEngine engine, JdbcClient jdbc, JsonMapper json,
			ApplicationEventPublisher events, Clock clock) {
		this.jobs = jobs;
		this.resumes = resumes;
		this.engine = engine;
		this.jdbc = jdbc;
		this.json = json;
		this.events = events;
		this.clock = clock;
	}

	@Transactional
	public void analyze(long jobId) {
		JobView job = this.jobs.get(jobId);
		Optional<ResumeProfile> resume = this.resumes.defaultResume();
		Optional<Stored> previous = stored(jobId);
		Outcome outcome = this.engine.run(job, resume, previous.map(Stored::reusable).orElse(Optional.empty()),
				feedbackExamples());
		save(jobId, resume.map(ResumeProfile::id).orElse(null), outcome, previous.map(Stored::attempts).orElse(0));
		this.events.publishEvent(new JobAnalyzed(jobId, outcome.matchScore()));
	}

	/** Etapa do LLM pendente ou com falha cuja espera já passou; chamada pelo agendador de novas tentativas. */
	public List<Long> dueForRetry(int limit) {
		return this.jdbc.sql("""
				select job_id from job_analysis
				where status in ('PENDENTE', 'FALHOU') and attempts < :max
				  and (next_attempt_at is null or next_attempt_at <= :now)
				order by next_attempt_at nulls first
				limit :limit
				""")
			.param("max", MAX_ATTEMPTS)
			.param("now", Timestamp.from(this.clock.instant()))
			.param("limit", limit)
			.query(Long.class)
			.list();
	}

	/**
	 * O currículo padrão mudou: recalcula a parte determinística de todas as vagas ainda abertas na hora e deixa
	 * o ajuste do LLM para o agendador, que processa aos poucos.
	 */
	@Transactional
	public int rescoreOpenJobs() {
		List<Long> open = this.jdbc.sql("""
				select ja.job_id from job_analysis ja
				left join application a on a.job_id = ja.job_id
				where a.id is null or a.status not in ('OFERTA', 'RECUSADA', 'DESISTI')
				""")
			.query(Long.class)
			.list();
		for (long jobId : open) {
			try {
				JobView job = this.jobs.get(jobId);
				Optional<ResumeProfile> resume = this.resumes.defaultResume();
				Stored previous = stored(jobId).orElseThrow();
				Outcome outcome = this.engine.deterministicRescore(job, resume, previous.reusable());
				save(jobId, resume.map(ResumeProfile::id).orElse(null), outcome, 0);
			}
			catch (RuntimeException ex) {
				log.warn("Não foi possível repontuar a vaga {}: {}", jobId, ex.getMessage());
			}
		}
		return open.size();
	}

	private void save(long jobId, Long resumeId, Outcome outcome, int previousAttempts) {
		boolean failed = "FALHOU".equals(outcome.status());
		int attempts = failed ? previousAttempts + 1 : 0;
		Instant now = this.clock.instant();
		Instant nextAttempt = failed ? now.plus(Duration.ofMinutes(15L * (1L << Math.min(attempts, 6)))) : null;
		this.jdbc.sql("""
				insert into job_analysis (job_id, status, seniority, required_skills, nice_to_have, work_model, summary,
				                          employer_kind, client_sectors, contract_type, resume_version_id,
				                          deterministic_score, llm_adjustment, match_score, matched_skills, gaps,
				                          justification, model_version, attempts, next_attempt_at, last_error, analyzed_at)
				values (:jobId, :status, :seniority, cast(:required as jsonb), cast(:nice as jsonb), :workModel, :summary,
				        :employerKind, cast(:clientSectors as jsonb), :contractType, :resumeId,
				        :deterministic, :adjustment, :score, cast(:matched as jsonb), cast(:gaps as jsonb),
				        :justification, :modelVersion, :attempts, :nextAttempt, :error, :now)
				on conflict (job_id) do update set
				    status = excluded.status, seniority = excluded.seniority, required_skills = excluded.required_skills,
				    nice_to_have = excluded.nice_to_have, work_model = excluded.work_model, summary = excluded.summary,
				    employer_kind = excluded.employer_kind, client_sectors = excluded.client_sectors,
				    contract_type = excluded.contract_type, resume_version_id = excluded.resume_version_id,
				    deterministic_score = excluded.deterministic_score, llm_adjustment = excluded.llm_adjustment,
				    match_score = excluded.match_score, matched_skills = excluded.matched_skills, gaps = excluded.gaps,
				    justification = excluded.justification, model_version = excluded.model_version,
				    attempts = excluded.attempts, next_attempt_at = excluded.next_attempt_at,
				    last_error = excluded.last_error, analyzed_at = excluded.analyzed_at
				""")
			.param("jobId", jobId)
			.param("status", outcome.status())
			.param("seniority", outcome.requirements().seniority())
			.param("required", this.json.writeValueAsString(outcome.requirements().requiredSkills()))
			.param("nice", this.json.writeValueAsString(outcome.requirements().niceToHave()))
			.param("workModel", outcome.requirements().workModel())
			.param("summary", outcome.requirements().summary())
			.param("employerKind", outcome.requirements().employerKind())
			.param("clientSectors", this.json.writeValueAsString(outcome.requirements().clientSectors()))
			.param("contractType", outcome.requirements().contractType())
			.param("resumeId", resumeId)
			.param("deterministic", outcome.deterministicScore())
			.param("adjustment", outcome.llmAdjustment())
			.param("score", outcome.matchScore())
			.param("matched", this.json.writeValueAsString(outcome.matchedSkills()))
			.param("gaps", this.json.writeValueAsString(outcome.gaps()))
			.param("justification", outcome.justification())
			.param("modelVersion", outcome.modelVersion())
			.param("attempts", attempts)
			.param("nextAttempt", (nextAttempt != null) ? Timestamp.from(nextAttempt) : null)
			.param("error", outcome.error())
			.param("now", Timestamp.from(now))
			.update();
	}

	private Optional<Stored> stored(long jobId) {
		return this.jdbc.sql("""
				select seniority, required_skills::text as required, nice_to_have::text as nice, work_model, summary,
				       employer_kind, client_sectors::text as sectors, contract_type, model_version, attempts
				from job_analysis where job_id = :jobId
				""")
			.param("jobId", jobId)
			.query((rs, row) -> new Stored(new AnalysisEngine.StoredRequirements(rs.getString("seniority"),
					readList(rs.getString("required")), readList(rs.getString("nice")), rs.getString("work_model"),
					rs.getString("summary"), rs.getString("employer_kind"), readList(rs.getString("sectors")),
					rs.getString("contract_type"), rs.getString("model_version")), rs.getInt("attempts")))
			.optional();
	}

	/** Últimas vagas avaliadas com "gostei" ou "não gostei", como exemplos de preferência para o LLM. */
	private List<AnalysisEngine.FeedbackExample> feedbackExamples() {
		return this.jdbc.sql("""
				select j.title, j.company, f.rating, f.reasons
				from job_feedback f join job j on j.id = f.job_id
				where f.rating in ('GOSTEI', 'NAO_GOSTEI')
				order by f.updated_at desc
				limit 10
				""")
			.query((rs, row) -> {
				Array reasons = rs.getArray("reasons");
				List<String> list = (reasons == null) ? List.of() : Arrays.asList((String[]) reasons.getArray());
				return new AnalysisEngine.FeedbackExample(rs.getString("title"), rs.getString("company"),
						rs.getString("rating"), list);
			})
			.list();
	}

	@SuppressWarnings("unchecked")
	private List<String> readList(String value) {
		return (value == null) ? List.of() : (List<String>) this.json.readValue(value, List.class);
	}

	private record Stored(AnalysisEngine.StoredRequirements requirements, int attempts) {

		Optional<AnalysisEngine.StoredRequirements> reusable() {
			return Optional.of(this.requirements);
		}

	}

}
