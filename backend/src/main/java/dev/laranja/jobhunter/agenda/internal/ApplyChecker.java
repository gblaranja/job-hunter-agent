package dev.laranja.jobhunter.agenda.internal;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import dev.laranja.jobhunter.agenda.ApplyCheck;
import dev.laranja.jobhunter.agenda.ApplyCheck.Warning;
import dev.laranja.jobhunter.shared.NotFoundException;
import dev.laranja.jobhunter.shared.web.LinkVerifier;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * Confere a vaga antes de você aplicar: o link ainda no ar, os eliminatórios que você não atende, a legitimidade
 * baixa ou suspeita, o score abaixo de 60 e se você já tem 3 ou mais candidaturas na empresa em 180 dias.
 */
@Component
public class ApplyChecker {

	static final int LOW_SCORE = 60;

	static final int MANY_APPLICATIONS = 3;

	private static final Duration COMPANY_WINDOW = Duration.ofDays(180);

	private final JdbcClient jdbc;

	private final LinkVerifier links;

	private final JsonMapper json;

	private final Clock clock;

	ApplyChecker(JdbcClient jdbc, LinkVerifier links, JsonMapper json, Clock clock) {
		this.jdbc = jdbc;
		this.links = links;
		this.json = json;
		this.clock = clock;
	}

	public ApplyCheck check(long jobId) {
		record Facts(@Nullable String url, @Nullable String company, @Nullable Long companyId,
				@Nullable String legitimacy, @Nullable String legitimacyReasons, @Nullable Integer matchScore,
				@Nullable String knockouts, @Nullable String companyName) {
		}
		Facts facts = this.jdbc.sql("""
				select j.url, j.company, j.company_id, j.legitimacy, j.legitimacy_reasons::text as legitimacy_reasons,
				       an.match_score, an.knockouts::text as knockouts, j.company as company_name
				from job j left join job_analysis an on an.job_id = j.id
				where j.id = :jobId
				""")
			.param("jobId", jobId)
			.query(Facts.class)
			.optional()
			.orElseThrow(() -> NotFoundException.of("Vaga", jobId));
		List<Warning> warnings = new ArrayList<>();
		String linkStatus = "SEM_LINK";
		if (facts.url() != null && !facts.url().isBlank()) {
			linkStatus = this.links.check(facts.url()).name();
			if (linkStatus.equals("REMOVIDO")) {
				warnings.add(new Warning("LINK_FORA_DO_AR", "O link da vaga parece fora do ar ou encerrado."));
			}
		}
		for (String reason : failedKnockouts(facts.knockouts())) {
			warnings.add(new Warning("ELIMINATORIO", "Eliminatório que você não atende: " + reason));
		}
		if ("BAIXA".equals(facts.legitimacy()) || "SUSPEITA".equals(facts.legitimacy())) {
			List<String> reasons = strings(facts.legitimacyReasons());
			warnings.add(new Warning("LEGITIMIDADE",
					("SUSPEITA".equals(facts.legitimacy()) ? "Suspeita de golpe" : "Possível vaga fantasma")
							+ (reasons.isEmpty() ? "." : ": " + String.join("; ", reasons) + ".")));
		}
		if (facts.matchScore() != null && facts.matchScore() < LOW_SCORE) {
			warnings.add(new Warning("SCORE_BAIXO",
					"Aderência de " + facts.matchScore() + "%, abaixo de " + LOW_SCORE + "%: vale adaptar o currículo antes."));
		}
		if (facts.companyId() != null) {
			long applications = this.jdbc.sql("""
					select count(*) from application a join job j on j.id = a.job_id
					where j.company_id = :companyId and j.id <> :jobId and a.applied_at >= :since
					""")
				.param("companyId", facts.companyId())
				.param("jobId", jobId)
				.param("since", Timestamp.from(this.clock.instant().minus(COMPANY_WINDOW)))
				.query(Long.class)
				.single();
			if (applications >= MANY_APPLICATIONS) {
				warnings.add(new Warning("MUITAS_CANDIDATURAS", "Você já aplicou " + applications + " vezes na "
						+ facts.companyName() + " nos últimos 180 dias; prefira a vaga mais aderente ou uma indicação."));
			}
		}
		return new ApplyCheck(jobId, linkStatus, List.copyOf(warnings));
	}

	private List<String> failedKnockouts(@Nullable String knockouts) {
		if (knockouts == null) {
			return List.of();
		}
		List<String> reasons = new ArrayList<>();
		for (JsonNode knockout : this.json.readTree(knockouts).values()) {
			if ("NAO_ATENDE".equals(knockout.path("status").asString(""))) {
				String reason = knockout.path("reason").asString("");
				reasons.add(reason.isBlank() ? knockout.path("text").asString("") : reason);
			}
		}
		return reasons;
	}

	private List<String> strings(@Nullable String array) {
		if (array == null) {
			return List.of();
		}
		return this.json.readTree(array).values().stream().map(node -> node.asString("")).toList();
	}

}
