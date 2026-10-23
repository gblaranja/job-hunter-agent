package dev.laranja.jobhunter.jobs.internal;

import java.time.Instant;

import dev.laranja.jobhunter.jobs.JobOrigin;
import dev.laranja.jobhunter.jobs.JobView;
import dev.laranja.jobhunter.jobs.ManualJobCommand;
import dev.laranja.jobhunter.jobs.WorkModel;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "job")
public class Job {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Enumerated(EnumType.STRING)
	@Column(name = "source")
	private JobOrigin origin;

	private String externalId;

	private String fingerprint;

	private String title;

	private String company;

	private String location;

	@Enumerated(EnumType.STRING)
	private WorkModel workModel;

	private String salaryRange;

	private String url;

	private String rawDescription;

	private Instant collectedAt;

	protected Job() {
	}

	public static Job manual(ManualJobCommand command, String fingerprint, Instant now) {
		Job job = new Job();
		job.origin = JobOrigin.MANUAL;
		job.fingerprint = fingerprint;
		job.title = command.title().strip();
		job.company = command.company().strip();
		job.location = blankToNull(command.location());
		job.workModel = (command.workModel() != null) ? command.workModel() : WorkModel.NAO_INFORMADO;
		job.salaryRange = blankToNull(command.salaryRange());
		job.url = blankToNull(command.url());
		job.rawDescription = blankToNull(command.description());
		job.collectedAt = now;
		return job;
	}

	public JobView toView() {
		return new JobView(this.id, this.origin, this.title, this.company, this.location, this.workModel,
				this.salaryRange, this.url, this.rawDescription, this.collectedAt);
	}

	public Long getId() {
		return this.id;
	}

	public JobOrigin getOrigin() {
		return this.origin;
	}

	private static String blankToNull(String value) {
		return (value == null || value.isBlank()) ? null : value.strip();
	}

}
