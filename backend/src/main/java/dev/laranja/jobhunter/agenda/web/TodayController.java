package dev.laranja.jobhunter.agenda.web;

import dev.laranja.jobhunter.agenda.ApplyCheck;
import dev.laranja.jobhunter.agenda.TodayAgenda;
import dev.laranja.jobhunter.agenda.TodayQueries;
import dev.laranja.jobhunter.agenda.internal.ApplyChecker;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
class TodayController {

	private final TodayQueries today;

	private final ApplyChecker checker;

	TodayController(TodayQueries today, ApplyChecker checker) {
		this.today = today;
		this.checker = checker;
	}

	/** Fila do dia: tudo o que pede ação hoje. */
	@GetMapping("/api/today")
	TodayAgenda today() {
		return this.today.today();
	}

	/**
	 * Avisos antes de aplicar: link fora do ar, eliminatórios falhos, legitimidade baixa ou suspeita, score abaixo
	 * de 60 e 3 ou mais candidaturas na empresa em 180 dias. Confere o link na hora (até 15 s).
	 */
	@GetMapping("/api/jobs/{id}/apply-check")
	ApplyCheck applyCheck(@PathVariable long id) {
		return this.checker.check(id);
	}

}
