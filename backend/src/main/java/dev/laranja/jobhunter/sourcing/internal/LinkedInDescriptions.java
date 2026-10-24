package dev.laranja.jobhunter.sourcing.internal;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import dev.laranja.jobhunter.jobs.JobCatalog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * Completa as vagas dos alertas do LinkedIn com a descrição da página pública da vaga. O LinkedIn bloqueia
 * rajadas (HTTP 429), então vai uma vaga por vez, as mais recentes primeiro. Num bloqueio, para tudo por um tempo
 * que dobra a cada bloqueio seguido. Vaga removida não é tentada de novo; sem descrição ou com erro, até
 * {@value #MAX_ATTEMPTS} vezes, com 6 horas de intervalo.
 */
@Component
class LinkedInDescriptions {

	static final int MAX_ATTEMPTS = 3;

	private static final Duration RETRY_AFTER = Duration.ofHours(6);

	private static final Duration MAX_BACKOFF = Duration.ofHours(12);

	private static final Logger log = LoggerFactory.getLogger(LinkedInDescriptions.class);

	private final SourcingProperties.LinkedInDescriptions config;

	private final JobCatalog jobs;

	private final JdbcClient jdbc;

	private final Clock clock;

	private final HttpClient http = HttpClient.newBuilder()
		.followRedirects(HttpClient.Redirect.NORMAL)
		.connectTimeout(Duration.ofSeconds(10))
		.build();

	private Instant pausedUntil = Instant.MIN;

	private Duration nextBackoff;

	LinkedInDescriptions(SourcingProperties properties, JobCatalog jobs, JdbcClient jdbc, Clock clock) {
		this.config = properties.linkedinDescriptions();
		this.jobs = jobs;
		this.jdbc = jdbc;
		this.clock = clock;
		this.nextBackoff = this.config.backoff();
	}

	/** Busca a descrição da próxima vaga da fila. */
	synchronized Outcome fetchNext() {
		Instant now = this.clock.instant();
		if (!this.config.enabled()) {
			return Outcome.DESLIGADA;
		}
		if (now.isBefore(this.pausedUntil)) {
			return Outcome.PAUSADA;
		}
		Optional<Pending> next = next(now);
		if (next.isEmpty()) {
			return Outcome.FILA_VAZIA;
		}
		Pending job = next.get();
		Outcome outcome = fetch(job);
		if (outcome == Outcome.BLOQUEADA) {
			this.pausedUntil = now.plus(this.nextBackoff);
			log.warn("LinkedIn bloqueou a busca de descrições (HTTP 429); nova tentativa às {}.", this.pausedUntil);
			this.nextBackoff = min(this.nextBackoff.multipliedBy(2), MAX_BACKOFF);
			return outcome;
		}
		this.nextBackoff = this.config.backoff();
		record(job.id(), outcome, now);
		return outcome;
	}

	private Outcome fetch(Pending job) {
		HttpRequest request = HttpRequest
			.newBuilder(URI.create(this.config.baseUrl() + "/jobs-guest/jobs/api/jobPosting/" + job.externalId()))
			.timeout(Duration.ofSeconds(20))
			.header("User-Agent", "Mozilla/5.0 (X11; Linux x86_64) JobHunterAgent/1.0")
			.header("Accept-Language", "pt-BR,pt;q=0.9,en;q=0.8")
			.GET()
			.build();
		try {
			HttpResponse<String> response = this.http.send(request, HttpResponse.BodyHandlers.ofString());
			int status = response.statusCode();
			// 999 é como o LinkedIn responde a acessos que ele considera automatizados.
			if (status == 429 || status == 999) {
				return Outcome.BLOQUEADA;
			}
			if (status == 404 || status == 410) {
				return Outcome.REMOVIDA;
			}
			if (status != 200) {
				log.info("Descrição da vaga {} indisponível: HTTP {}.", job.id(), status);
				return Outcome.ERRO;
			}
			String description = LinkedInJobPage.description(response.body());
			if (description == null) {
				return Outcome.SEM_DESCRICAO;
			}
			this.jobs.attachDescription(job.id(), description);
			return Outcome.OK;
		}
		catch (IOException ex) {
			log.info("Descrição da vaga {} indisponível: {}", job.id(), ex.getMessage());
			return Outcome.ERRO;
		}
		catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
			return Outcome.ERRO;
		}
	}

	private Optional<Pending> next(Instant now) {
		return this.jdbc.sql("""
				select j.id, j.external_id from job j
				left join linkedin_description_fetch f on f.job_id = j.id
				where j.source = 'LINKEDIN_ALERTA' and j.raw_description is null and j.external_id is not null
				  and j.closed_at is null
				  and (f.job_id is null
				       or (f.status in ('SEM_DESCRICAO', 'ERRO') and f.attempts < :max and f.last_attempt_at < :retryBefore))
				order by f.job_id is not null, j.collected_at desc, j.id desc
				limit 1
				""")
			.param("max", MAX_ATTEMPTS)
			.param("retryBefore", Timestamp.from(now.minus(RETRY_AFTER)))
			.query(Pending.class)
			.optional();
	}

	private void record(long jobId, Outcome outcome, Instant now) {
		this.jdbc.sql("""
				insert into linkedin_description_fetch (job_id, status, attempts, last_attempt_at)
				values (:jobId, :status, 1, :now)
				on conflict (job_id) do update
				set status = excluded.status, attempts = linkedin_description_fetch.attempts + 1,
				    last_attempt_at = excluded.last_attempt_at
				""")
			.param("jobId", jobId)
			.param("status", outcome.name())
			.param("now", Timestamp.from(now))
			.update();
	}

	private static Duration min(Duration a, Duration b) {
		return (a.compareTo(b) <= 0) ? a : b;
	}

	/** Para os testes, que dividem o mesmo contexto: esquece um bloqueio anterior. */
	synchronized void clearPause() {
		this.pausedUntil = Instant.MIN;
		this.nextBackoff = this.config.backoff();
	}

	private record Pending(long id, String externalId) {
	}

	enum Outcome {

		OK, SEM_DESCRICAO, REMOVIDA, ERRO, BLOQUEADA, PAUSADA, FILA_VAZIA, DESLIGADA

	}

}
