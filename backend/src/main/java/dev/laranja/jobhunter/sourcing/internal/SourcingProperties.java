package dev.laranja.jobhunter.sourcing.internal;

import java.time.Duration;

import org.jspecify.annotations.Nullable;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Fontes complementares aos alertas. Os ATS (Greenhouse, Lever, Ashby, SmartRecruiters, Workable, Teamtailor e
 * Recruitee) consultam as empresas monitoradas e alvo com o ATS configurado; as fontes amplas ficam desligadas até
 * você habilitar (confira os termos de cada uma).
 */
@ConfigurationProperties("jobhunter.sourcing")
record SourcingProperties(@DefaultValue Ats ats, @DefaultValue Remotive remotive, @DefaultValue GitHub github,
		@DefaultValue Adzuna adzuna, @DefaultValue LinkedInDescriptions linkedinDescriptions,
		@DefaultValue JobImport jobImport) {

	/**
	 * Importação de vaga por link.
	 * @param allowPrivateHosts aceita endereços de rede interna e loopback; só nos testes (servidor simulado local)
	 */
	record JobImport(@DefaultValue("false") boolean allowPrivateHosts) {
	}

	/**
	 * Endereços das APIs dos ATS (trocados pelo WireMock nos testes).
	 * @param leverJobsBaseUrl página pública dos job boards da Lever, usada para conferir um slug com lista vazia
	 * @param smartrecruitersDetailsPerCompany quantas vagas novas da SmartRecruiters têm a descrição buscada por
	 * empresa a cada coleta (a listagem não traz a descrição, e cada vaga é uma requisição); as demais ficam para a
	 * próxima coleta
	 * @param workableBaseUrl o endereço documentado, {@code www.workable.com/api/accounts/{conta}}, redireciona para
	 * este
	 * @param teamtailorBaseUrl modelo com {@code {slug}}: cada empresa tem o próprio subdomínio
	 * @param recruiteeBaseUrl modelo com {@code {slug}}: cada empresa tem o próprio subdomínio
	 */
	record Ats(@DefaultValue("https://boards-api.greenhouse.io") String greenhouseBaseUrl,
			@DefaultValue("https://api.lever.co") String leverBaseUrl,
			@DefaultValue("https://jobs.lever.co") String leverJobsBaseUrl,
			@DefaultValue("https://api.ashbyhq.com") String ashbyBaseUrl,
			@DefaultValue("https://api.smartrecruiters.com") String smartrecruitersBaseUrl,
			@DefaultValue("50") int smartrecruitersDetailsPerCompany,
			@DefaultValue("https://apply.workable.com") String workableBaseUrl,
			@DefaultValue("https://{slug}.teamtailor.com") String teamtailorBaseUrl,
			@DefaultValue("https://{slug}.recruitee.com") String recruiteeBaseUrl) {
	}

	record Remotive(@DefaultValue("false") boolean enabled, @DefaultValue("https://remotive.com") String baseUrl,
			@DefaultValue("software-dev") String category) {
	}

	record GitHub(@DefaultValue("false") boolean enabled, @DefaultValue("https://api.github.com") String baseUrl,
			@DefaultValue("backend-br/vagas") String repository, @Nullable String token) {
	}

	/**
	 * Busca da descrição das vagas dos alertas do LinkedIn na página pública da vaga (sem login). Desligada por padrão:
	 * a cláusula 8.2 dos termos do LinkedIn proíbe robôs e scripts que copiam dados do site.
	 * @param interval espera entre uma vaga e a próxima
	 * @param backoff primeira pausa depois de um bloqueio (HTTP 429); dobra a cada bloqueio seguido, até 12 horas
	 */
	record LinkedInDescriptions(@DefaultValue("false") boolean enabled,
			@DefaultValue("https://www.linkedin.com") String baseUrl, @DefaultValue("PT1M") Duration interval,
			@DefaultValue("PT30M") Duration backoff) {
	}

	record Adzuna(@Nullable String appId, @Nullable String appKey, @DefaultValue("br") String country,
			@DefaultValue("https://api.adzuna.com") String baseUrl) {

		boolean enabled() {
			return this.appId != null && !this.appId.isBlank() && this.appKey != null && !this.appKey.isBlank();
		}

	}

}
