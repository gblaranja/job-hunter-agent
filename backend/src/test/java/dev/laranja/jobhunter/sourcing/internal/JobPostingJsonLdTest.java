package dev.laranja.jobhunter.sourcing.internal;

import java.io.IOException;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;

import dev.laranja.jobhunter.jobs.WorkModel;
import dev.laranja.jobhunter.sourcing.internal.JobPostingJsonLd.Posting;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import org.springframework.core.io.ClassPathResource;

import static org.assertj.core.api.Assertions.assertThat;

class JobPostingJsonLdTest {

	private final JsonMapper json = JsonMapper.builder().build();

	@Test
	void readsTheJobPostingInsideAGraphFromAnAtsPage() throws IOException {
		String html = new ClassPathResource("fixtures/import/greenhouse-jsonld.html").getContentAsString(StandardCharsets.UTF_8);
		List<String> blocks = Jsoup.parse(html).select("script[type=application/ld+json]").stream().map(Element::data).toList();

		Posting posting = JobPostingJsonLd.parse(this.json, blocks).orElseThrow();

		assertThat(posting.title()).isEqualTo("Pessoa Desenvolvedora Backend Sênior");
		assertThat(posting.company()).isEqualTo("Banco Alfa");
		assertThat(posting.location()).isEqualTo("Remoto (Brasil)");
		assertThat(posting.workModel()).isEqualTo(WorkModel.REMOTO);
		assertThat(posting.salaryRange()).isEqualTo("R$ 15.000–19.000/mês");
		assertThat(posting.description()).contains("Time de pagamentos.").contains("- Java 21 e Spring Boot").doesNotContain("<p>");
		assertThat(posting.validThrough()).isNotNull();
	}

	@Test
	void mapsPlainLocationsAndSingleSalaryAndIgnoresOtherTypes() {
		String block = """
				[{"@type": "Organization", "name": "X"},
				 {"@type": ["JobPosting"], "title": "QA", "hiringOrganization": "Beta",
				  "jobLocation": [{"address": {"addressLocality": "Recife", "addressRegion": "PE"}},
				                  {"address": {"addressCountry": {"name": "Portugal"}}}],
				  "baseSalary": {"currency": "USD", "value": {"value": "40", "unitText": "HOUR"}}}]""";

		Posting posting = JobPostingJsonLd.parse(this.json, List.of("{quebrado", block)).orElseThrow();

		assertThat(posting.company()).isEqualTo("Beta");
		assertThat(posting.location()).isEqualTo("Recife, PE · Portugal");
		assertThat(posting.workModel()).isNull();
		assertThat(posting.salaryRange()).isEqualTo("USD 40/hora");
		assertThat(JobPostingJsonLd.parse(this.json, List.of("{\"@type\": \"Organization\"}"))).isEmpty();
	}

	@Test
	void internalAddressesAreBlocked() throws IOException {
		for (String address : List.of("127.0.0.1", "10.1.2.3", "172.16.0.9", "192.168.1.1", "169.254.169.254",
				"100.64.0.1", "0.0.0.0", "::1", "fd00::1")) {
			assertThat(PageFetcher.isInternal(InetAddress.getByName(address))).as(address).isTrue();
		}
		assertThat(PageFetcher.isInternal(InetAddress.getByName("8.8.8.8"))).isFalse();
		assertThat(PageFetcher.isInternal(InetAddress.getByName("2001:4860:4860::8888"))).isFalse();
	}

}
