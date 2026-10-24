package dev.laranja.jobhunter.recognition.web;

import java.util.List;

import dev.laranja.jobhunter.recognition.GptwRecognitionService;
import dev.laranja.jobhunter.recognition.RankingImport;
import dev.laranja.jobhunter.recognition.RankingImport.ImportConfirmation;
import dev.laranja.jobhunter.recognition.RankingImport.ImportPreview;
import dev.laranja.jobhunter.recognition.RankingImport.ImportSource;
import dev.laranja.jobhunter.recognition.RecognitionReview;
import dev.laranja.jobhunter.recognition.RecognitionView;
import dev.laranja.jobhunter.recognition.internal.CachedSearch;
import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Selos GPTW: verificação sob demanda, fila de revisão, histórico por empresa e importação em lote. */
@RestController
class RecognitionController {

	private final GptwRecognitionService gptw;

	private final RecognitionReview review;

	private final RankingImport imports;

	private final CachedSearch search;

	RecognitionController(GptwRecognitionService gptw, RecognitionReview review, RankingImport imports,
			CachedSearch search) {
		this.gptw = gptw;
		this.review = review;
		this.imports = imports;
		this.search = search;
	}

	/** Se a verificação automática está ligada, o uso de buscas no mês e o tamanho da fila de revisão. */
	@GetMapping("/api/recognitions/status")
	RecognitionStatus status() {
		return new RecognitionStatus(this.gptw.enabled(), this.search.enabled(), this.search.provider(),
				this.search.usedThisMonth(), this.search.monthlyLimit(), this.review.pending().size());
	}

	/** Selos com confiança baixa ou homônimos, esperando a sua confirmação. */
	@GetMapping("/api/recognitions/review")
	List<RecognitionView> pending() {
		return this.review.pending();
	}

	@PostMapping("/api/recognitions/{id}/confirm")
	RecognitionView confirm(@PathVariable long id) {
		return this.review.confirm(id);
	}

	@PostMapping("/api/recognitions/{id}/reject")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void reject(@PathVariable long id) {
		this.review.reject(id);
	}

	/** Histórico de certificações e rankings da empresa, com as evidências. */
	@GetMapping("/api/companies/{companyId}/recognitions")
	List<RecognitionView> history(@PathVariable long companyId) {
		return this.review.history(companyId);
	}

	/** Busca o selo agora (as respostas em cache não gastam consultas). */
	@PostMapping("/api/companies/{companyId}/recognitions/check")
	RecognitionView check(@PathVariable long companyId) {
		return this.gptw.check(companyId);
	}

	/** Reverifica agora as empresas com a verificação vencida (normalmente roda todo mês). */
	@PostMapping("/api/recognitions/recheck")
	RecheckResult recheck() {
		return new RecheckResult(this.gptw.recheckExpired());
	}

	/** Prévia da importação de uma lista de premiadas (texto colado ou link). */
	@PostMapping("/api/recognitions/import")
	ImportPreview preview(@Valid @RequestBody ImportSource source) {
		return this.imports.preview(source);
	}

	@PostMapping("/api/recognitions/import/confirm")
	ImportResult confirmImport(@Valid @RequestBody ImportConfirmation confirmation) {
		return new ImportResult(this.imports.confirm(confirmation));
	}

	/**
	 * @param enabled busca automática ligada (API de busca e Claude API configuradas)
	 * @param searchesThisMonth consultas pagas feitas no mês (respostas em cache não contam)
	 */
	record RecognitionStatus(boolean enabled, boolean searchConfigured, String provider, int searchesThisMonth,
			int monthlyLimit, int pendingReview) {
	}

	record RecheckResult(int checked) {
	}

	record ImportResult(int companies) {
	}

}
