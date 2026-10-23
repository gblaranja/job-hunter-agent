package dev.laranja.jobhunter.sourcing.internal;

import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import dev.laranja.jobhunter.jobs.JobOrigin;
import dev.laranja.jobhunter.jobs.RawJob;
import dev.laranja.jobhunter.jobs.WorkModel;
import dev.laranja.jobhunter.profile.SearchProfiles;
import dev.laranja.jobhunter.sourcing.JobSource;
import org.jspecify.annotations.Nullable;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/**
 * Fontes amplas, desligadas por padrão: Remotive (vagas remotas, pede crédito à fonte e no máximo algumas consultas
 * por dia), as issues do repositório backend-br/vagas no GitHub e a API da Adzuna (exige app_id e app_key). Só entram
 * as vagas com alguma palavra-chave dos perfis de busca.
 */
@Configuration(proxyBeanMethods = false)
class BroadSources {

	/** "[Remoto] Desenvolvedor Java Pleno @ Empresa" ou "... na Empresa". */
	private static final Pattern GITHUB_TITLE = Pattern.compile("^((?:\\s*\\[[^\\]]*\\])*)\\s*(.+?)(?:\\s+(?:@|na|no|-|\\|)\\s+([^@|]+))?$");

	private static final Pattern TAG = Pattern.compile("\\[([^\\]]*)\\]");

	@Bean
	JobSource remotiveSource(RestClient.Builder http, SourcingProperties properties) {
		SourcingProperties.Remotive config = properties.remotive();
		RestClient client = http.clone().baseUrl(config.baseUrl()).build();
		return new BroadSource("REMOTIVE", config.enabled()) {

			@Override
			public void collect(Consumer<RawJob> sink) {
				RemotiveResponse response = client.get()
					.uri("/api/remote-jobs?category={category}", config.category())
					.retrieve()
					.body(RemotiveResponse.class);
				if (response == null || response.jobs() == null) {
					return;
				}
				for (RemotiveJob job : response.jobs()) {
					sink.accept(new RawJob(JobOrigin.REMOTIVE, String.valueOf(job.id()), job.title(), job.companyName(),
							job.candidateRequiredLocation(), WorkModel.REMOTO, blankToNull(job.salary()), job.url(),
							HtmlText.toText(job.description(), false)));
				}
			}
		};
	}

	@Bean
	JobSource githubVagasSource(RestClient.Builder http, SourcingProperties properties) {
		SourcingProperties.GitHub config = properties.github();
		RestClient.Builder builder = http.clone()
			.baseUrl(config.baseUrl())
			.defaultHeader("Accept", "application/vnd.github+json");
		if (config.token() != null && !config.token().isBlank()) {
			builder.defaultHeader("Authorization", "Bearer " + config.token());
		}
		RestClient client = builder.build();
		return new BroadSource("GITHUB", config.enabled()) {

			@Override
			public void collect(Consumer<RawJob> sink) {
				GitHubIssue[] issues = client.get()
					// "dono/repositório" vai inteiro no caminho; como variável de URI a barra seria codificada.
					.uri("/repos/" + config.repository() + "/issues?state=open&per_page=100")
					.retrieve()
					.body(GitHubIssue[].class);
				if (issues == null) {
					return;
				}
				for (GitHubIssue issue : issues) {
					if (issue.pullRequest() != null) {
						continue;
					}
					sink.accept(fromIssue(issue));
				}
			}
		};
	}

	@Bean
	JobSource adzunaSource(RestClient.Builder http, SourcingProperties properties, SearchProfiles profiles) {
		SourcingProperties.Adzuna config = properties.adzuna();
		RestClient client = http.clone().baseUrl(config.baseUrl()).build();
		return new BroadSource("ADZUNA", config.enabled()) {

			@Override
			public void collect(Consumer<RawJob> sink) {
				List<String> keywords = profiles.activeFilter().keywords();
				if (keywords.isEmpty()) {
					return;
				}
				AdzunaResponse response = client.get()
					.uri(uri -> uri.path("/v1/api/jobs/{country}/search/1")
						.queryParam("app_id", config.appId())
						.queryParam("app_key", config.appKey())
						.queryParam("results_per_page", 50)
						.queryParam("max_days_old", 7)
						.queryParam("what_or", String.join(" ", keywords))
						.build(config.country()))
					.retrieve()
					.body(AdzunaResponse.class);
				if (response == null || response.results() == null) {
					return;
				}
				for (AdzunaJob job : response.results()) {
					sink.accept(new RawJob(JobOrigin.ADZUNA, job.id(), job.title(),
							(job.company() != null && job.company().displayName() != null) ? job.company().displayName()
									: "Não informada",
							(job.location() != null) ? job.location().displayName() : null, null, salary(job), job.redirectUrl(),
							job.description()));
				}
			}
		};
	}

	static RawJob fromIssue(GitHubIssue issue) {
		Matcher matcher = GITHUB_TITLE.matcher(issue.title().strip());
		String tags = "";
		String title = issue.title().strip();
		String company = "Não informada";
		if (matcher.matches()) {
			tags = String.join(", ", TAG.matcher(matcher.group(1)).results().map(tag -> tag.group(1).strip()).toList());
			title = matcher.group(2).strip();
			if (matcher.group(3) != null) {
				company = matcher.group(3).strip();
			}
		}
		String labels = (issue.labels() == null) ? ""
				: String.join(", ", issue.labels().stream().map(GitHubLabel::name).toList());
		String location = !tags.isEmpty() ? tags : (labels.isEmpty() ? null : labels);
		WorkModel workModel = null;
		String lower = (tags + " " + labels).toLowerCase(Locale.ROOT);
		if (lower.contains("remoto") || lower.contains("remote")) {
			workModel = WorkModel.REMOTO;
		}
		else if (lower.contains("híbrido") || lower.contains("hibrido")) {
			workModel = WorkModel.HIBRIDO;
		}
		else if (lower.contains("presencial")) {
			workModel = WorkModel.PRESENCIAL;
		}
		return new RawJob(JobOrigin.GITHUB, String.valueOf(issue.number()), title, company, location, workModel, null,
				issue.htmlUrl(), issue.body());
	}

	private static @Nullable String salary(AdzunaJob job) {
		if (job.salaryMin() == null) {
			return null;
		}
		if (job.salaryMax() == null || job.salaryMax().equals(job.salaryMin())) {
			return String.format("%.0f", job.salaryMin());
		}
		return String.format("%.0f – %.0f", job.salaryMin(), job.salaryMax());
	}

	private static @Nullable String blankToNull(@Nullable String value) {
		return (value == null || value.isBlank()) ? null : value;
	}

	private abstract static class BroadSource implements JobSource {

		private final String id;

		private final boolean enabled;

		BroadSource(String id, boolean enabled) {
			this.id = id;
			this.enabled = enabled;
		}

		@Override
		public String id() {
			return this.id;
		}

		@Override
		public boolean available() {
			return this.enabled;
		}

		@Override
		public boolean broad() {
			return true;
		}

	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record RemotiveResponse(@Nullable List<RemotiveJob> jobs) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record RemotiveJob(long id, String url, String title, @JsonProperty("company_name") String companyName,
			@JsonProperty("candidate_required_location") @Nullable String candidateRequiredLocation,
			@Nullable String salary, @Nullable String description) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record GitHubIssue(long number, String title, @JsonProperty("html_url") String htmlUrl, @Nullable String body,
			@Nullable List<GitHubLabel> labels, @JsonProperty("pull_request") @Nullable Object pullRequest) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record GitHubLabel(String name) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record AdzunaResponse(@Nullable List<AdzunaJob> results) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record AdzunaJob(String id, String title, @Nullable AdzunaName company, @Nullable AdzunaName location,
			@JsonProperty("redirect_url") String redirectUrl, @Nullable String description,
			@JsonProperty("salary_min") @Nullable Double salaryMin,
			@JsonProperty("salary_max") @Nullable Double salaryMax) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record AdzunaName(@JsonProperty("display_name") @Nullable String displayName) {
	}

}
