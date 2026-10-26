package dev.laranja.jobhunter.sourcing.internal;

import dev.laranja.jobhunter.companies.AtsBoard;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class AtsLinksTest {

	@ParameterizedTest
	@CsvSource({ "https://boards.greenhouse.io/nubank, GREENHOUSE, nubank",
			"https://job-boards.greenhouse.io/nubank/jobs/123?gh_src=x, GREENHOUSE, nubank",
			"https://boards.greenhouse.io/embed/job_board?for=stone&b=x, GREENHOUSE, stone",
			"https://jobs.lever.co/wildlifestudios/, LEVER, wildlifestudios",
			"https://jobs.ashbyhq.com/cloudwalk/abc-123, ASHBY, cloudwalk",
			"https://careers.smartrecruiters.com/BoschGroup, SMARTRECRUITERS, BoschGroup",
			"https://jobs.smartrecruiters.com/Visa/744000-dev, SMARTRECRUITERS, Visa",
			"https://apply.workable.com/huggingface/, WORKABLE, huggingface",
			"https://acme.workable.com, WORKABLE, acme", "https://ZETA.teamtailor.com/jobs, TEAMTAILOR, zeta",
			"https://acme.na.teamtailor.com/jobs, TEAMTAILOR, acme.na", "http://bunq.recruitee.com/o/sre, RECRUITEE, bunq" })
	void recognizesTheAtsAndTheSlug(String link, String ats, String slug) {
		assertThat(AtsLinks.detect(link)).contains(new AtsBoard(ats, slug));
	}

	@ParameterizedTest
	@ValueSource(strings = { "https://carreiras.itau.com.br/vagas", "https://www.teamtailor.com/pt-br/",
			"https://app.recruitee.com/", "https://apply.workable.com/j/AB12CD", "https://jobs.lever.co/",
			"ftp://jobs.lever.co/acme", "não é um link", "https://acme.gupy.io/" })
	void ignoresLinksOutsideTheSupportedJobBoards(String link) {
		assertThat(AtsLinks.detect(link)).isEmpty();
	}

}
