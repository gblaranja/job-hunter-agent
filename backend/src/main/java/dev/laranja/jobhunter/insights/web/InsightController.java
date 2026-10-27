package dev.laranja.jobhunter.insights.web;

import java.util.List;

import dev.laranja.jobhunter.insights.FunnelBreakdown;
import dev.laranja.jobhunter.insights.InsightReport;
import dev.laranja.jobhunter.insights.Insights;
import dev.laranja.jobhunter.insights.LlmUsage;
import dev.laranja.jobhunter.shared.ai.LlmBudget;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/insights")
class InsightController {

	private final Insights insights;

	private final LlmBudget budget;

	InsightController(Insights insights, LlmBudget budget) {
		this.insights = insights;
		this.budget = budget;
	}

	/** Último relatório; 204 enquanto nenhum foi gerado. */
	@GetMapping("/latest")
	ResponseEntity<InsightReport> latest() {
		return this.insights.latest().map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.noContent().build());
	}

	/** Gera o relatório agora; com a Claude API, leva até alguns minutos. */
	@PostMapping("/generate")
	InsightReport generate() {
		return this.insights.generate();
	}

	/** Funil por fonte, canal, tempo até aplicar e adaptação; calculado na hora, sem a Claude API. */
	@GetMapping("/funnel")
	FunnelBreakdown funnel() {
		return this.insights.breakdown();
	}

	@GetMapping("/usage")
	List<LlmUsage> usage() {
		return this.insights.monthUsage();
	}

	/** Gasto do mês por tarefa contra o orçamento ({@code jobhunter.ai.budget.monthly-usd}). */
	@GetMapping("/budget")
	List<LlmBudget.LlmBudgetStatus> budget() {
		return this.budget.status();
	}

}
