package dev.laranja.jobhunter.sourcing.internal;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import dev.laranja.jobhunter.companies.CompanyMonitoring;
import dev.laranja.jobhunter.companies.TrackedCompany;
import dev.laranja.jobhunter.jobs.JobCatalog;
import dev.laranja.jobhunter.jobs.JobOrigin;
import dev.laranja.jobhunter.jobs.RawJob;
import dev.laranja.jobhunter.jobs.WorkModel;
import org.jspecify.annotations.Nullable;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/** Adapters dos ATS com API pública de job board: Greenhouse, Lever e Ashby. */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(SourcingProperties.class)
class AtsSources {

	@Bean
	AtsSource greenhouseSource(CompanyMonitoring companies, JobCatalog catalog, RestClient.Builder http,
			SourcingProperties properties) {
		RestClient client = http.clone().baseUrl(properties.ats().greenhouseBaseUrl()).build();
		return new AtsSource(companies, catalog) {

			@Override
			JobOrigin origin() {
				return JobOrigin.GREENHOUSE;
			}

			@Override
			List<RawJob> list(TrackedCompany company) {
				GreenhouseBoard board = client.get()
					.uri("/v1/boards/{slug}/jobs?content=true", company.atsSlug())
					.retrieve()
					.body(GreenhouseBoard.class);
				return (board == null || board.jobs() == null) ? List.of() : board.jobs()
					.stream()
					.map(job -> new RawJob(JobOrigin.GREENHOUSE, String.valueOf(job.id()), job.title(), company.name(),
							(job.location() != null) ? job.location().name() : null, null, null, job.absoluteUrl(),
							HtmlText.toText(job.content(), true)))
					.toList();
			}
		};
	}

	@Bean
	AtsSource leverSource(CompanyMonitoring companies, JobCatalog catalog, RestClient.Builder http,
			SourcingProperties properties) {
		RestClient client = http.clone().baseUrl(properties.ats().leverBaseUrl()).build();
		return new AtsSource(companies, catalog) {

			@Override
			JobOrigin origin() {
				return JobOrigin.LEVER;
			}

			@Override
			List<RawJob> list(TrackedCompany company) {
				LeverPosting[] postings = client.get()
					.uri("/v0/postings/{slug}?mode=json", company.atsSlug())
					.retrieve()
					.body(LeverPosting[].class);
				return (postings == null) ? List.of() : Arrays.stream(postings)
					.map(posting -> new RawJob(JobOrigin.LEVER, posting.id(), posting.text(), company.name(),
							(posting.categories() != null) ? posting.categories().location() : null,
							workModel(posting.workplaceType()), null, posting.hostedUrl(), posting.fullDescription()))
					.toList();
			}
		};
	}

	@Bean
	AtsSource ashbySource(CompanyMonitoring companies, JobCatalog catalog, RestClient.Builder http,
			SourcingProperties properties) {
		RestClient client = http.clone().baseUrl(properties.ats().ashbyBaseUrl()).build();
		return new AtsSource(companies, catalog) {

			@Override
			JobOrigin origin() {
				return JobOrigin.ASHBY;
			}

			@Override
			List<RawJob> list(TrackedCompany company) {
				AshbyBoard board = client.get()
					.uri("/posting-api/job-board/{slug}", company.atsSlug())
					.retrieve()
					.body(AshbyBoard.class);
				return (board == null || board.jobs() == null) ? List.of() : board.jobs()
					.stream()
					.map(job -> new RawJob(JobOrigin.ASHBY, job.id(), job.title(), company.name(), job.location(),
							Boolean.TRUE.equals(job.isRemote()) ? WorkModel.REMOTO : workModel(job.workplaceType()), null,
							job.jobUrl(), (job.descriptionPlain() != null) ? job.descriptionPlain()
									: HtmlText.toText(job.descriptionHtml(), false)))
					.toList();
			}
		};
	}

	static @Nullable WorkModel workModel(@Nullable String value) {
		if (value == null) {
			return null;
		}
		String normalized = value.toLowerCase(Locale.ROOT).replace("-", "").replace(" ", "");
		return switch (normalized) {
			case "remote" -> WorkModel.REMOTO;
			case "hybrid" -> WorkModel.HIBRIDO;
			case "onsite", "inoffice" -> WorkModel.PRESENCIAL;
			default -> null;
		};
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record GreenhouseBoard(@Nullable List<GreenhouseJob> jobs) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record GreenhouseJob(long id, String title, @JsonProperty("absolute_url") String absoluteUrl,
			@Nullable GreenhouseLocation location, @Nullable String content) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record GreenhouseLocation(@Nullable String name) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record LeverPosting(String id, String text, String hostedUrl, @Nullable LeverCategories categories,
			@Nullable String workplaceType, @Nullable String descriptionPlain, @Nullable List<LeverList> lists,
			@Nullable String additionalPlain) {

		String fullDescription() {
			StringBuilder text = new StringBuilder((this.descriptionPlain != null) ? this.descriptionPlain.strip() : "");
			if (this.lists != null) {
				for (LeverList list : this.lists) {
					text.append("\n\n").append(list.text()).append("\n").append(HtmlText.toText(list.content(), false));
				}
			}
			if (this.additionalPlain != null) {
				text.append("\n\n").append(this.additionalPlain.strip());
			}
			return text.toString().strip();
		}

	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record LeverCategories(@Nullable String location, @Nullable String commitment, @Nullable String team) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record LeverList(String text, String content) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record AshbyBoard(@Nullable List<AshbyJob> jobs) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record AshbyJob(String id, String title, @Nullable String location, @Nullable Boolean isRemote,
			@Nullable String workplaceType, String jobUrl, @Nullable String descriptionPlain,
			@Nullable String descriptionHtml) {
	}

}
