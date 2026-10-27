package dev.laranja.jobhunter.analysis.internal;

import dev.laranja.jobhunter.analysis.JobAnalyzer;
import dev.laranja.jobhunter.shared.ai.LlmClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Na subida, com a Claude API configurada, marca para nova extração as vagas relevantes analisadas com um prompt de
 * extração anterior (por exemplo, sem os eliminatórios). Idempotente: as já reextraídas têm a versão atual.
 */
@Component
class ReextractionBackfill implements ApplicationRunner {

	private static final Logger log = LoggerFactory.getLogger(ReextractionBackfill.class);

	private final JobAnalyzer analyzer;

	private final LlmClient llm;

	ReextractionBackfill(JobAnalyzer analyzer, LlmClient llm) {
		this.analyzer = analyzer;
		this.llm = llm;
	}

	@Override
	public void run(ApplicationArguments args) {
		if (!this.llm.enabled()) {
			return;
		}
		int marked = this.analyzer.markForReextraction();
		if (marked > 0) {
			log.info("{} vagas marcadas para nova extração dos requisitos.", marked);
		}
	}

}
