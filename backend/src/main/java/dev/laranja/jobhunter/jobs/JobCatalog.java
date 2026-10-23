package dev.laranja.jobhunter.jobs;

import java.time.Clock;

import dev.laranja.jobhunter.jobs.internal.Job;
import dev.laranja.jobhunter.jobs.internal.JobFingerprint;
import dev.laranja.jobhunter.jobs.internal.JobRepository;
import dev.laranja.jobhunter.shared.ConflictException;
import dev.laranja.jobhunter.shared.NotFoundException;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Catálogo de vagas: a única porta de escrita da tabela {@code job}. */
@Service
@Transactional(readOnly = true)
public class JobCatalog {

	private final JobRepository jobs;

	private final ApplicationEventPublisher events;

	private final Clock clock;

	JobCatalog(JobRepository jobs, ApplicationEventPublisher events, Clock clock) {
		this.jobs = jobs;
		this.events = events;
		this.clock = clock;
	}

	public JobView get(long id) {
		return this.jobs.findById(id).map(Job::toView).orElseThrow(() -> NotFoundException.of("Vaga", id));
	}

	@Transactional
	public JobView registerManual(ManualJobCommand command) {
		String fingerprint = JobFingerprint.of(command.company(), command.title(), command.location());
		this.jobs.findByFingerprint(fingerprint).ifPresent(existing -> {
			throw new ConflictException("Essa vaga já está cadastrada.", existing.getId());
		});
		Job job = this.jobs.save(Job.manual(command, fingerprint, this.clock.instant()));
		this.events.publishEvent(new JobCollected(job.getId(), job.getOrigin()));
		return job.toView();
	}

}
