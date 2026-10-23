package dev.laranja.jobhunter.analysis.web;

import dev.laranja.jobhunter.analysis.JobAnalyzer;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
class AnalysisController {

	private final JobAnalyzer analyzer;

	AnalysisController(JobAnalyzer analyzer) {
		this.analyzer = analyzer;
	}

	/** Reanalisa a vaga agora (por exemplo, depois de colar a descrição completa). */
	@PostMapping("/api/jobs/{id}/analysis")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void reanalyze(@PathVariable long id) {
		this.analyzer.analyze(id);
	}

}
