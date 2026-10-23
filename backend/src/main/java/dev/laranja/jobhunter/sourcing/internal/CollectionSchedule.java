package dev.laranja.jobhunter.sourcing.internal;

import javax.sql.DataSource;

import dev.laranja.jobhunter.shared.ConflictException;
import dev.laranja.jobhunter.sourcing.JobCollector;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.provider.jdbctemplate.JdbcTemplateLockProvider;
import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * Coleta diária (7h, horário de Brasília, por padrão). O ShedLock garante uma única execução mesmo com mais de
 * uma instância da aplicação no ar.
 */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
@EnableSchedulerLock(defaultLockAtMostFor = "PT30M")
@ConditionalOnProperty(name = "jobhunter.sourcing.scheduler-enabled", havingValue = "true", matchIfMissing = true)
class CollectionSchedule {

	private static final Logger log = LoggerFactory.getLogger(CollectionSchedule.class);

	private final JobCollector collector;

	CollectionSchedule(JobCollector collector) {
		this.collector = collector;
	}

	@Scheduled(cron = "${jobhunter.sourcing.cron}", zone = "America/Sao_Paulo")
	@SchedulerLock(name = "collect-jobs", lockAtMostFor = "PT30M", lockAtLeastFor = "PT1M")
	void dailyCollection() {
		try {
			this.collector.collect(JobCollector.Trigger.AGENDADA);
		}
		catch (ConflictException ex) {
			log.info("Coleta agendada ignorada: {}", ex.getMessage());
		}
	}

	@Bean
	LockProvider lockProvider(DataSource dataSource) {
		return new JdbcTemplateLockProvider(JdbcTemplateLockProvider.Configuration.builder()
			.withJdbcTemplate(new JdbcTemplate(dataSource))
			.usingDbTime()
			.build());
	}

}
