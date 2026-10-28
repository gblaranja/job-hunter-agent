package dev.laranja.jobhunter.sourcing.internal;

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import dev.laranja.jobhunter.companies.AtsSlugStatus;
import dev.laranja.jobhunter.companies.CompanyMonitoring;
import dev.laranja.jobhunter.companies.TrackedCompany;
import dev.laranja.jobhunter.jobs.JobCatalog;
import dev.laranja.jobhunter.jobs.JobOrigin;
import dev.laranja.jobhunter.jobs.RawJob;
import dev.laranja.jobhunter.jobs.WorkModel;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Adapters dos ATS com API pública de job board: Greenhouse, Lever, Ashby, SmartRecruiters, Workable, Teamtailor e
 * Recruitee.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(SourcingProperties.class)
class AtsSources {

	private static final Logger log = LoggerFactory.getLogger(AtsSources.class);

	private static final int SMARTRECRUITERS_PAGE = 100;

	/** Teto de segurança da paginação: empresas globais passam de 5 mil vagas abertas. */
	private static final int SMARTRECRUITERS_MAX_POSTINGS = 10_000;

	private static final int TEAMTAILOR_MAX_PAGES = 20;

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

			@Override
			AtsSlugStatus verify(String slug) {
				return probe(() -> client.get().uri("/v1/boards/{slug}", slug).retrieve().toBodilessEntity());
			}
		};
	}

	@Bean
	AtsSource leverSource(CompanyMonitoring companies, JobCatalog catalog, RestClient.Builder http,
			SourcingProperties properties) {
		RestClient client = http.clone().baseUrl(properties.ats().leverBaseUrl()).build();
		RestClient jobsPage = http.clone().baseUrl(properties.ats().leverJobsBaseUrl()).build();
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

			/** Lista vazia não prova que o slug existe: nesse caso, confere a página pública do job board. */
			@Override
			AtsSlugStatus verify(String slug) {
				LeverPosting[][] postings = new LeverPosting[1][];
				AtsSlugStatus status = probe(() -> postings[0] = client.get()
					.uri("/v0/postings/{slug}?mode=json&limit=1", slug)
					.retrieve()
					.body(LeverPosting[].class));
				if (status != AtsSlugStatus.ENCONTRADO || (postings[0] != null && postings[0].length > 0)) {
					return status;
				}
				return probe(() -> jobsPage.get().uri("/{slug}", slug).retrieve().toBodilessEntity());
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

			@Override
			AtsSlugStatus verify(String slug) {
				return probe(() -> client.get().uri("/posting-api/job-board/{slug}", slug).retrieve().toBodilessEntity());
			}
		};
	}

	/**
	 * SmartRecruiters: a listagem é paginada e não traz a descrição, que vem no detalhe de cada vaga. O detalhe só é
	 * buscado para as vagas ainda sem descrição, até o limite por empresa; as demais ficam para a próxima coleta.
	 */
	@Bean
	AtsSource smartRecruitersSource(CompanyMonitoring companies, JobCatalog catalog, RestClient.Builder http,
			SourcingProperties properties) {
		RestClient client = http.clone().baseUrl(properties.ats().smartrecruitersBaseUrl()).build();
		int detailsPerCompany = properties.ats().smartrecruitersDetailsPerCompany();
		return new AtsSource(companies, catalog) {

			@Override
			JobOrigin origin() {
				return JobOrigin.SMARTRECRUITERS;
			}

			@Override
			List<RawJob> list(TrackedCompany company) {
				List<SmartRecruitersPosting> postings = new ArrayList<>();
				int offset = 0;
				while (offset < SMARTRECRUITERS_MAX_POSTINGS) {
					SmartRecruitersPage page = client.get()
						.uri("/v1/companies/{company}/postings?limit={limit}&offset={offset}", company.atsSlug(),
								SMARTRECRUITERS_PAGE, offset)
						.retrieve()
						.body(SmartRecruitersPage.class);
					if (page == null || page.content() == null || page.content().isEmpty()) {
						break;
					}
					postings.addAll(page.content());
					offset += page.content().size();
					if (offset >= page.totalFound()) {
						break;
					}
				}
				Set<String> described = catalog.describedExternalIds(JobOrigin.SMARTRECRUITERS, company.id());
				int budget = detailsPerCompany;
				List<RawJob> jobs = new ArrayList<>();
				for (SmartRecruitersPosting posting : postings) {
					String url = "https://jobs.smartrecruiters.com/" + company.atsSlug() + "/" + posting.id();
					String description = null;
					if (!described.contains(posting.id()) && budget > 0) {
						budget--;
						try {
							SmartRecruitersDetail detail = client.get()
								.uri("/v1/companies/{company}/postings/{id}", company.atsSlug(), posting.id())
								.retrieve()
								.body(SmartRecruitersDetail.class);
							if (detail != null) {
								description = detail.description();
								url = (detail.postingUrl() != null) ? detail.postingUrl() : url;
							}
						}
						catch (RestClientException ex) {
							log.info("SMARTRECRUITERS: sem o detalhe da vaga {} de {}: {}", posting.id(), company.name(),
									ex.getMessage());
						}
					}
					SmartRecruitersLocation location = posting.location();
					jobs.add(new RawJob(JobOrigin.SMARTRECRUITERS, posting.id(), posting.name(), company.name(),
							(location != null) ? location.label() : null,
							(location != null) ? location.workModel() : null, null, url, description));
				}
				return jobs;
			}

			/** Empresa inexistente e empresa sem vagas devolvem a mesma lista vazia: só uma vaga confirma o slug. */
			@Override
			AtsSlugStatus verify(String slug) {
				SmartRecruitersPage[] page = new SmartRecruitersPage[1];
				AtsSlugStatus status = probe(() -> page[0] = client.get()
					.uri("/v1/companies/{company}/postings?limit=1", slug)
					.retrieve()
					.body(SmartRecruitersPage.class));
				if (status == AtsSlugStatus.ENCONTRADO && (page[0] == null || page[0].totalFound() == 0)) {
					return AtsSlugStatus.NAO_CONFIRMADO;
				}
				return status;
			}
		};
	}

	/** Workable: a conta lista as vagas publicadas e, com {@code details=true}, as descrições. */
	@Bean
	AtsSource workableSource(CompanyMonitoring companies, JobCatalog catalog, RestClient.Builder http,
			SourcingProperties properties) {
		RestClient client = http.clone().baseUrl(properties.ats().workableBaseUrl()).build();
		return new AtsSource(companies, catalog) {

			@Override
			JobOrigin origin() {
				return JobOrigin.WORKABLE;
			}

			@Override
			List<RawJob> list(TrackedCompany company) {
				WorkableAccount account = client.get()
					.uri("/api/v1/widget/accounts/{slug}?details=true", company.atsSlug())
					.retrieve()
					.body(WorkableAccount.class);
				return (account == null || account.jobs() == null) ? List.of() : account.jobs()
					.stream()
					.map(job -> new RawJob(JobOrigin.WORKABLE, job.shortcode(), job.title(), company.name(),
							joined(job.city(), job.state(), job.country()),
							Boolean.TRUE.equals(job.telecommuting()) ? WorkModel.REMOTO : null, null, job.url(),
							HtmlText.toText(job.description(), false)))
					.toList();
			}

			@Override
			AtsSlugStatus verify(String slug) {
				return probe(() -> client.get().uri("/api/v1/widget/accounts/{slug}", slug).retrieve().toBodilessEntity());
			}
		};
	}

	/** Teamtailor: o site de carreira publica um JSON Feed com a descrição e o JSON-LD de cada vaga. */
	@Bean
	AtsSource teamtailorSource(CompanyMonitoring companies, JobCatalog catalog, RestClient.Builder http,
			SourcingProperties properties) {
		RestClient client = http.clone().build();
		String baseUrl = properties.ats().teamtailorBaseUrl();
		return new AtsSource(companies, catalog) {

			@Override
			JobOrigin origin() {
				return JobOrigin.TEAMTAILOR;
			}

			@Override
			List<RawJob> list(TrackedCompany company) {
				List<RawJob> jobs = new ArrayList<>();
				TeamtailorFeed feed = client.get()
					.uri(baseUrl + "/jobs.json", Map.of("slug", company.atsSlug()))
					.retrieve()
					.body(TeamtailorFeed.class);
				for (int page = 1; feed != null && page <= TEAMTAILOR_MAX_PAGES; page++) {
					if (feed.items() != null) {
						for (TeamtailorItem item : feed.items()) {
							JobPostingLd posting = item.jobPosting();
							jobs.add(new RawJob(JobOrigin.TEAMTAILOR, item.id(), item.title(), company.name(),
									(posting != null) ? posting.place() : null,
									(posting != null) ? posting.workModel() : null, null, item.url(),
									HtmlText.toText(item.contentHtml(), false)));
						}
					}
					if (feed.nextUrl() == null || feed.nextUrl().isBlank()) {
						break;
					}
					feed = client.get().uri(URI.create(feed.nextUrl())).retrieve().body(TeamtailorFeed.class);
				}
				return jobs;
			}

			@Override
			AtsSlugStatus verify(String slug) {
				return probe(() -> client.get().uri(baseUrl + "/jobs.json", Map.of("slug", slug)).retrieve().toBodilessEntity());
			}
		};
	}

	/** Recruitee: a API do site de carreira lista as vagas publicadas com descrição e requisitos. */
	@Bean
	AtsSource recruiteeSource(CompanyMonitoring companies, JobCatalog catalog, RestClient.Builder http,
			SourcingProperties properties) {
		RestClient client = http.clone().build();
		String baseUrl = properties.ats().recruiteeBaseUrl();
		return new AtsSource(companies, catalog) {

			@Override
			JobOrigin origin() {
				return JobOrigin.RECRUITEE;
			}

			@Override
			List<RawJob> list(TrackedCompany company) {
				RecruiteeOffers offers = client.get()
					.uri(baseUrl + "/api/offers/", Map.of("slug", company.atsSlug()))
					.retrieve()
					.body(RecruiteeOffers.class);
				return (offers == null || offers.offers() == null) ? List.of() : offers.offers()
					.stream()
					.map(offer -> new RawJob(JobOrigin.RECRUITEE, String.valueOf(offer.id()), offer.title(),
							company.name(), offer.location(), offer.workModel(), null, offer.careersUrl(),
							offer.fullDescription()))
					.toList();
			}

			@Override
			AtsSlugStatus verify(String slug) {
				return probe(() -> client.get().uri(baseUrl + "/api/offers/", Map.of("slug", slug)).retrieve().toBodilessEntity());
			}
		};
	}

	static @Nullable String joined(@Nullable String... parts) {
		String text = Arrays.stream(parts)
			.filter(part -> part != null && !part.isBlank())
			.map(String::strip)
			.distinct()
			.collect(Collectors.joining(", "));
		return text.isEmpty() ? null : text;
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
	record SmartRecruitersPage(int totalFound, @Nullable List<SmartRecruitersPosting> content) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record SmartRecruitersPosting(String id, String name, @Nullable SmartRecruitersLocation location) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record SmartRecruitersLocation(@Nullable String city, @Nullable String region, @Nullable String country,
			@Nullable String fullLocation, @Nullable Boolean remote, @Nullable Boolean hybrid) {

		@Nullable String label() {
			return (this.fullLocation != null && !this.fullLocation.isBlank()) ? this.fullLocation
					: joined(this.city, this.region, this.country);
		}

		@Nullable WorkModel workModel() {
			if (Boolean.TRUE.equals(this.remote)) {
				return WorkModel.REMOTO;
			}
			return Boolean.TRUE.equals(this.hybrid) ? WorkModel.HIBRIDO : null;
		}

	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record SmartRecruitersDetail(@Nullable String postingUrl, @Nullable SmartRecruitersJobAd jobAd) {

		/** Junta as seções do anúncio (empresa, vaga, requisitos e informações adicionais) com os títulos. */
		@Nullable String description() {
			if (this.jobAd == null || this.jobAd.sections() == null) {
				return null;
			}
			SmartRecruitersSections sections = this.jobAd.sections();
			String text = Stream
				.of(sections.companyDescription(), sections.jobDescription(), sections.qualifications(),
						sections.additionalInformation())
				.filter(section -> section != null && section.text() != null && !section.text().isBlank())
				.map(section -> ((section.title() != null) ? section.title() + "\n" : "")
						+ HtmlText.toText(section.text(), false))
				.collect(Collectors.joining("\n\n"));
			return text.isBlank() ? null : text;
		}

	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record SmartRecruitersJobAd(@Nullable SmartRecruitersSections sections) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record SmartRecruitersSections(@Nullable SmartRecruitersSection companyDescription,
			@Nullable SmartRecruitersSection jobDescription, @Nullable SmartRecruitersSection qualifications,
			@Nullable SmartRecruitersSection additionalInformation) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record SmartRecruitersSection(@Nullable String title, @Nullable String text) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record WorkableAccount(@Nullable String name, @Nullable List<WorkableJob> jobs) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record WorkableJob(String shortcode, String title, String url, @Nullable String city, @Nullable String state,
			@Nullable String country, @Nullable Boolean telecommuting, @Nullable String description) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record TeamtailorFeed(@Nullable List<TeamtailorItem> items, @JsonProperty("next_url") @Nullable String nextUrl) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record TeamtailorItem(String id, String title, String url, @JsonProperty("content_html") @Nullable String contentHtml,
			@JsonProperty("_jobposting") @Nullable JobPostingLd jobPosting) {
	}

	/** O pedaço do JSON-LD {@code JobPosting} que a listagem usa: local e trabalho remoto. */
	@JsonIgnoreProperties(ignoreUnknown = true)
	record JobPostingLd(@Nullable String jobLocationType,
			@JsonFormat(with = JsonFormat.Feature.ACCEPT_SINGLE_VALUE_AS_ARRAY) @Nullable List<LdPlace> jobLocation) {

		@Nullable String place() {
			if (this.jobLocation == null) {
				return null;
			}
			String text = this.jobLocation.stream()
				.map(LdPlace::address)
				.filter(address -> address != null)
				.map(address -> joined(address.addressLocality(), address.country()))
				.filter(label -> label != null)
				.distinct()
				.collect(Collectors.joining("; "));
			return text.isEmpty() ? null : text;
		}

		@Nullable WorkModel workModel() {
			return "TELECOMMUTE".equalsIgnoreCase(this.jobLocationType) ? WorkModel.REMOTO : null;
		}

	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record LdPlace(@Nullable LdAddress address) {
	}

	/** {@code addressCountry} pode ser o código do país ou um objeto {@code Country} com o nome. */
	@JsonIgnoreProperties(ignoreUnknown = true)
	record LdAddress(@Nullable String addressLocality, @Nullable Object addressCountry) {

		@Nullable String country() {
			if (this.addressCountry instanceof String code) {
				return code;
			}
			if (this.addressCountry instanceof Map<?, ?> country && country.get("name") instanceof String name) {
				return name;
			}
			return null;
		}

	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record RecruiteeOffers(@Nullable List<RecruiteeOffer> offers) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record RecruiteeOffer(long id, String title, @JsonProperty("careers_url") String careersUrl,
			@Nullable String location, @Nullable Boolean remote, @Nullable Boolean hybrid,
			@JsonProperty("on_site") @Nullable Boolean onSite, @Nullable String description,
			@Nullable String requirements) {

		@Nullable WorkModel workModel() {
			if (Boolean.TRUE.equals(this.hybrid)) {
				return WorkModel.HIBRIDO;
			}
			if (Boolean.TRUE.equals(this.remote)) {
				return WorkModel.REMOTO;
			}
			return Boolean.TRUE.equals(this.onSite) ? WorkModel.PRESENCIAL : null;
		}

		@Nullable String fullDescription() {
			String text = Stream.of(HtmlText.toText(this.description, false), HtmlText.toText(this.requirements, false))
				.filter(part -> part != null && !part.isBlank())
				.collect(Collectors.joining("\n\n"));
			return text.isEmpty() ? null : text;
		}

	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record AshbyJob(String id, String title, @Nullable String location, @Nullable Boolean isRemote,
			@Nullable String workplaceType, String jobUrl, @Nullable String descriptionPlain,
			@Nullable String descriptionHtml) {
	}

}
