package dev.laranja.jobhunter.niches.web;

import java.util.List;

import dev.laranja.jobhunter.niches.CompanySeeds;
import dev.laranja.jobhunter.niches.CompanySeeds.SeedSourceView;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Importações das bases públicas de empresas (Banco Central e Receita). */
@RestController
@RequestMapping("/api/admin/seeds")
class SeedController {

	private final CompanySeeds seeds;

	SeedController(CompanySeeds seeds) {
		this.seeds = seeds;
	}

	@GetMapping
	List<SeedSourceView> sources() {
		return this.seeds.sources();
	}

	/** Dispara a importação em segundo plano; o resultado chega como notificação. */
	@PostMapping("/{source}/run")
	@ResponseStatus(HttpStatus.ACCEPTED)
	void run(@PathVariable String source) {
		this.seeds.start(source);
	}

}
