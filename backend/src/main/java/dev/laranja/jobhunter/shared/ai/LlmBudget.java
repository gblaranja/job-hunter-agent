package dev.laranja.jobhunter.shared.ai;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.jspecify.annotations.Nullable;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * Gasto do mês por tarefa contra o orçamento de {@link LlmBudgetProperties}. O custo usa a mesma tabela de preços do
 * card de uso em Insights: Haiku a US$ 0,10 / 0,50 e Opus a US$ 4 / 20 por milhão de tokens (entrada / saída), cache
 * lido a 10% e escrito a 125% da entrada.
 */
@Component
@EnableConfigurationProperties(LlmBudgetProperties.class)
public class LlmBudget {

	private static final ZoneId ZONE = ZoneId.of("America/Sao_Paulo");

	private final LlmBudgetProperties properties;

	private final JdbcClient jdbc;

	private final Clock clock;

	LlmBudget(LlmBudgetProperties properties, JdbcClient jdbc, Clock clock) {
		this.properties = properties;
		this.jdbc = jdbc;
		this.clock = clock;
	}

	/** Se a tarefa ainda pode chamar a API neste mês. */
	public boolean allows(String task) {
		BigDecimal limit = this.properties.monthlyUsd().get(task);
		return limit == null || spent(task).compareTo(limit) < 0;
	}

	/** Situação de cada tarefa com orçamento ou com gasto no mês. */
	public List<LlmBudgetStatus> status() {
		Map<String, BigDecimal> spent = new TreeMap<>();
		this.jdbc.sql(costSql("group by task"))
			.param("since", monthStart())
			.query((rs, row) -> spent.put(rs.getString("task"), rs.getBigDecimal("cost")))
			.list();
		this.properties.monthlyUsd().keySet().forEach(task -> spent.putIfAbsent(task, BigDecimal.ZERO));
		return spent.entrySet().stream().map(entry -> {
			BigDecimal limit = this.properties.monthlyUsd().get(entry.getKey());
			return new LlmBudgetStatus(entry.getKey(), entry.getValue(), limit,
					limit != null && entry.getValue().compareTo(limit) >= 0);
		}).toList();
	}

	BigDecimal spent(String task) {
		return this.jdbc.sql(costSql("and task = :task group by task"))
			.param("since", monthStart())
			.param("task", task)
			.query((rs, row) -> rs.getBigDecimal("cost"))
			.optional()
			.orElse(BigDecimal.ZERO);
	}

	private Timestamp monthStart() {
		LocalDate first = LocalDate.now(this.clock.withZone(ZONE)).withDayOfMonth(1);
		return Timestamp.from(first.atStartOfDay(ZONE).toInstant());
	}

	private static String costSql(String tail) {
		return """
				select task, round(sum(
				           (input_tokens + cache_read_tokens * 0.1 + cache_write_tokens * 1.25) * p.input
				           + output_tokens * p.output) / 1000000, 4) as cost
				from llm_usage u
				cross join lateral (select case when u.model like 'claude-opus%%' then 4.0 else 0.10 end as input,
				                           case when u.model like 'claude-opus%%' then 20.0 else 0.50 end as output) p
				where u.created_at >= :since %s
				""".formatted(tail);
	}

	/**
	 * Gasto do mês de uma tarefa.
	 * @param limitUsd orçamento; nulo quando a tarefa não tem limite
	 * @param exhausted o orçamento acabou e a tarefa está no determinístico
	 */
	public record LlmBudgetStatus(String task, BigDecimal spentUsd, @Nullable BigDecimal limitUsd, boolean exhausted) {
	}

}
