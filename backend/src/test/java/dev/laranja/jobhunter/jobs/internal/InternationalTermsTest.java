package dev.laranja.jobhunter.jobs.internal;

import dev.laranja.jobhunter.jobs.InternationalEligibility;
import dev.laranja.jobhunter.jobs.internal.JobScopeDetector.InternationalTerms;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class InternationalTermsTest {

	@ParameterizedTest
	@CsvSource(delimiter = '|', textBlock = """
			Senior Java Developer, Brazil | Brasil (Remoto)          | We are hiring engineers to work with us.                         | BRASIL
			Backend Engineer              | Remote                   | We hire across Latin America, in USD.                            | LATAM
			Backend Engineer (LATAM)      |                          |                                                                  | LATAM
			Backend Engineer              | Remote                   | This role is open to candidates in the Americas.                 | LATAM
			Backend Engineer              | Remote                   | Candidates must be authorized to work in the United States.     | PAIS_ESPECIFICO
			Backend Engineer              | Remote - US only         |                                                                  | PAIS_ESPECIFICO
			Backend Engineer              | Remote                   | You can work from anywhere within the EU.                        | PAIS_ESPECIFICO
			Backend Engineer              | United States (Remote)   |                                                                  | PAIS_ESPECIFICO
			Backend Engineer              | Lisboa, Portugal         |                                                                  | PAIS_ESPECIFICO
			Backend Engineer              | Remote                   | We are a fully remote team and hire worldwide.                   | GLOBAL
			Backend Engineer              | Anywhere                 | Work from anywhere in the world.                                 | GLOBAL
			Backend Engineer              | Remote                   | Join our team building payments.                                 | NAO_INFORMADO
			""")
	void eligibility(String title, String location, String description, InternationalEligibility expected) {
		assertThat(JobScopeDetector.international(title, location, description).eligibility()).isEqualTo(expected);
	}

	@Test
	void mustBeBasedInLatamIsLatamNotRestricted() {
		assertThat(JobScopeDetector.international("Engineer", "Remote", "You must be based in LATAM.").eligibility())
			.isEqualTo(InternationalEligibility.LATAM);
	}

	@Test
	void readsTimezoneAndContractor() {
		InternationalTerms terms = JobScopeDetector.international("Senior Engineer", "Remote", """
				This is a contractor position paid in USD. You need at least 4 hours of overlap with EST, and
				our team is spread across UTC-3 to UTC+1.
				""");
		assertThat(terms.contractor()).isTrue();
		assertThat(terms.timezone()).isEqualTo("UTC-3");

		InternationalTerms named = JobScopeDetector.international("Engineer", null,
				"Working hours aligned with US time zones.");
		assertThat(named.timezone()).isEqualTo("US time zones");
		assertThat(named.contractor()).isFalse();

		assertThat(JobScopeDetector.international("Engineer", null, "We are a B2B SaaS. Hours: EST.").timezone())
			.isEqualTo("EST");
		assertThat(JobScopeDetector.international("Engineer", null, "We are a B2B SaaS. The best of the best.")
			.timezone()).isNull();
	}

	@Test
	void eligibleFromBrazilHidesOnlyRestrictedJobs() {
		assertThat(InternationalEligibility.PAIS_ESPECIFICO.eligibleFromBrazil()).isFalse();
		assertThat(InternationalEligibility.NAO_INFORMADO.eligibleFromBrazil()).isTrue();
		assertThat(InternationalEligibility.GLOBAL.eligibleFromBrazil()).isTrue();
	}

}
