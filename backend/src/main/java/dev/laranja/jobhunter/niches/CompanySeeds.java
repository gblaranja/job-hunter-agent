package dev.laranja.jobhunter.niches;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import dev.laranja.jobhunter.companies.CompanyDirectory;
import dev.laranja.jobhunter.companies.SeedCompanyCommand;
import dev.laranja.jobhunter.companies.SeededCompany;
import dev.laranja.jobhunter.niches.internal.CompanyNicheClassifier;
import dev.laranja.jobhunter.niches.internal.CompanySeedSource;
import dev.laranja.jobhunter.niches.internal.NicheDefinitions;
import dev.laranja.jobhunter.niches.internal.NicheDefinitions.NicheDef;
import dev.laranja.jobhunter.shared.BusinessRuleException;
import dev.laranja.jobhunter.shared.ConflictException;
import dev.laranja.jobhunter.shared.NotFoundException;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Importações das bases públicas de empresas ({@link CompanySeedSource}): cadastra as empresas como descobertas, já
 * no subsegmento do tipo de autorização, e recalcula o fit. O Banco Central roda todo mês; a base da Receita, só
 * pelo endpoint admin.
 */
@Service
@EnableConfigurationProperties(NichesProperties.class)
public class CompanySeeds {

	private static final Logger log = LoggerFactory.getLogger(CompanySeeds.class);

	private final List<CompanySeedSource> sources;

	private final CompanyDirectory directory;

	private final CompanyNicheClassifier classifier;

	private final NicheDefinitions niches;

	private final CompanyFits fits;

	private final ApplicationEventPublisher events;

	private final TransactionTemplate transactions;

	private final TaskExecutor executor;

	private final Set<String> running = ConcurrentHashMap.newKeySet();

	CompanySeeds(List<CompanySeedSource> sources, CompanyDirectory directory, CompanyNicheClassifier classifier,
			NicheDefinitions niches, CompanyFits fits, ApplicationEventPublisher events,
			PlatformTransactionManager transactionManager, @Qualifier("applicationTaskExecutor") TaskExecutor executor) {
		this.sources = sources;
		this.directory = directory;
		this.classifier = classifier;
		this.niches = niches;
		this.fits = fits;
		this.events = events;
		this.transactions = new TransactionTemplate(transactionManager);
		this.executor = executor;
	}

	public List<SeedSourceView> sources() {
		return this.sources.stream()
			.map(source -> new SeedSourceView(source.key(), source.label(), source.unavailableReason() == null,
					source.unavailableReason(), this.running.contains(source.key())))
			.toList();
	}

	@Scheduled(cron = "${jobhunter.niches.bcb.cron:0 0 3 1 * *}", zone = "America/Sao_Paulo")
	@SchedulerLock(name = "seed-bcb", lockAtMostFor = "PT1H")
	void monthlyBcb() {
		try {
			run("bcb");
		}
		catch (RuntimeException ex) {
			log.warn("Importação mensal do Banco Central falhou: {}", ex.getMessage());
		}
	}

	/** Dispara a importação em segundo plano; o fim vira uma notificação. */
	public void start(String key) {
		CompanySeedSource source = available(key);
		if (!this.running.add(source.key())) {
			throw new ConflictException("A importação de " + source.label() + " já está rodando.", null);
		}
		this.executor.execute(() -> {
			try {
				execute(source);
			}
			catch (RuntimeException ex) {
				log.warn("Importação {} falhou", source.key(), ex);
				publish(new CompanySeedCompleted(source.key(), source.label(), 0, 0, ex.getMessage()));
			}
			finally {
				this.running.remove(source.key());
			}
		});
	}

	/** Roda a importação agora, esperando o fim. */
	public SeedRun run(String key) {
		CompanySeedSource source = available(key);
		if (!this.running.add(source.key())) {
			throw new ConflictException("A importação de " + source.label() + " já está rodando.", null);
		}
		try {
			return execute(source);
		}
		finally {
			this.running.remove(source.key());
		}
	}

	private SeedRun execute(CompanySeedSource source) {
		Instant started = Instant.now();
		List<NicheDef> active = this.niches.active();
		NicheSource origin = "cnpj".equals(source.key()) ? NicheSource.CNPJ : NicheSource.BCB;
		AtomicInteger processed = new AtomicInteger();
		AtomicInteger created = new AtomicInteger();
		source.fetch(record -> {
			try {
				SeededCompany seeded = this.directory.upsertSeeded(new SeedCompanyCommand(record.name(), record.cnpj(),
						record.authorizationType(), record.kind(), record.website(), record.location()));
				this.classifier.assignFromSeed(seeded.companyId(), record.authorizationType(), origin, active);
				processed.incrementAndGet();
				if (seeded.created()) {
					created.incrementAndGet();
				}
			}
			catch (BusinessRuleException ex) {
				log.debug("Empresa ignorada na importação {}: {}", source.key(), ex.getMessage());
			}
		});
		this.fits.recomputeAll();
		SeedRun run = new SeedRun(source.key(), processed.get(), created.get(),
				Duration.between(started, Instant.now()).toMillis());
		log.info("Importação {}: {} empresas, {} novas.", source.key(), run.processed(), run.created());
		publish(new CompanySeedCompleted(source.key(), source.label(), run.processed(), run.created(), null));
		return run;
	}

	private void publish(CompanySeedCompleted event) {
		// os listeners do Modulith só recebem eventos publicados numa transação
		this.transactions.executeWithoutResult(status -> this.events.publishEvent(event));
	}

	private CompanySeedSource available(String key) {
		CompanySeedSource source = this.sources.stream()
			.filter(candidate -> candidate.key().equalsIgnoreCase(key))
			.findFirst()
			.orElseThrow(() -> new NotFoundException("Fonte de empresas " + key + " não existe."));
		if (source.unavailableReason() != null) {
			throw new BusinessRuleException(source.unavailableReason());
		}
		return source;
	}

	/**
	 * @param unavailableReason o que falta configurar, quando a fonte está desligada
	 */
	public record SeedSourceView(String key, String label, boolean available, @Nullable String unavailableReason,
			boolean running) {
	}

	public record SeedRun(String source, int processed, int created, long durationMillis) {
	}

}
