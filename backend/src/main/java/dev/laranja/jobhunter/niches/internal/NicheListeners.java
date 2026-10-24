package dev.laranja.jobhunter.niches.internal;

import dev.laranja.jobhunter.analysis.JobAnalyzed;
import dev.laranja.jobhunter.companies.CompanyDiscovered;
import dev.laranja.jobhunter.niches.CompanyFits;
import dev.laranja.jobhunter.recognition.RecognitionChanged;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Mantém nicho e fit em dia: empresa nova, vaga analisada (o tipo e o texto da vaga mudam a classificação) e selo
 * alterado disparam o recálculo da empresa; no domingo, depois das métricas, tudo é recalculado.
 */
@Component
class NicheListeners {

	private static final Logger log = LoggerFactory.getLogger(NicheListeners.class);

	private final CompanyNicheClassifier classifier;

	private final CompanyFits fits;

	private final JdbcClient jdbc;

	NicheListeners(CompanyNicheClassifier classifier, CompanyFits fits, JdbcClient jdbc) {
		this.classifier = classifier;
		this.fits = fits;
		this.jdbc = jdbc;
	}

	@ApplicationModuleListener
	void on(CompanyDiscovered event) {
		refresh(event.companyId());
	}

	@ApplicationModuleListener
	void on(JobAnalyzed event) {
		this.jdbc.sql("select company_id from job where id = :id and company_id is not null")
			.param("id", event.jobId())
			.query(Long.class)
			.optional()
			.ifPresent(this::refresh);
	}

	@ApplicationModuleListener
	void on(RecognitionChanged event) {
		this.fits.recompute(event.companyId());
	}

	@Scheduled(cron = "${jobhunter.niches.fit-cron:0 0 23 * * SUN}", zone = "America/Sao_Paulo")
	@SchedulerLock(name = "niche-fit", lockAtMostFor = "PT1H")
	void weekly() {
		int classified = this.classifier.classifyAll();
		int fits = this.fits.recomputeAll();
		log.info("Nichos: {} empresas classificadas, {} fits recalculados.", classified, fits);
	}

	private void refresh(long companyId) {
		this.classifier.classify(companyId);
		this.fits.recompute(companyId);
	}

}
