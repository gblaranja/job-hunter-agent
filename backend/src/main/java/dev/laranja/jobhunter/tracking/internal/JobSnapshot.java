package dev.laranja.jobhunter.tracking.internal;

import java.time.Instant;
import java.util.Objects;

import dev.laranja.jobhunter.jobs.JobView;
import dev.laranja.jobhunter.tracking.CaptureSource;
import dev.laranja.jobhunter.tracking.LinkStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Cópia congelada da vaga no momento em que você aplicou. As vagas costumam sair do ar antes da entrevista;
 * esta cópia é o que foi pedido de fato.
 */
@Entity
@Table(name = "job_snapshot")
public class JobSnapshot {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	private long applicationId;

	private String jobUrl;

	@Enumerated(EnumType.STRING)
	private LinkStatus linkStatus;

	private Instant linkCheckedAt;

	private int linkConsecutiveFailures;

	private String title;

	private String company;

	@Column(name = "description_md")
	private String descriptionMd;

	@Enumerated(EnumType.STRING)
	private CaptureSource captureSource;

	private String pagePdfKey;

	private Instant capturedAt;

	protected JobSnapshot() {
	}

	public static JobSnapshot capture(long applicationId, JobView job, CaptureSource source, Instant now) {
		return create(applicationId, job.url(), job.title(), job.company(), job.description(), source, now);
	}

	public static JobSnapshot manual(long applicationId, String url, String title, String company,
			String descriptionMd, Instant now) {
		return create(applicationId, url, title, company, descriptionMd, CaptureSource.MANUAL, now);
	}

	private static JobSnapshot create(long applicationId, String url, String title, String company,
			String descriptionMd, CaptureSource source, Instant now) {
		JobSnapshot snapshot = new JobSnapshot();
		snapshot.applicationId = applicationId;
		snapshot.jobUrl = url;
		snapshot.linkStatus = LinkStatus.NAO_VERIFICADO;
		snapshot.title = title;
		snapshot.company = company;
		snapshot.descriptionMd = descriptionMd;
		snapshot.captureSource = source;
		snapshot.capturedAt = now;
		return snapshot;
	}

	/** Correção feita por você: um link novo volta a ser "não verificado". */
	public void edit(String url, String title, String company, String descriptionMd) {
		if (!Objects.equals(url, this.jobUrl)) {
			this.linkStatus = LinkStatus.NAO_VERIFICADO;
			this.linkCheckedAt = null;
			this.linkConsecutiveFailures = 0;
		}
		this.jobUrl = url;
		this.title = title;
		this.company = company;
		this.descriptionMd = descriptionMd;
	}

	public void attachPagePdf(String key) {
		this.pagePdfKey = key;
	}

	public String getPagePdfKey() {
		return this.pagePdfKey;
	}

	public String getTitle() {
		return this.title;
	}

	public String getDescriptionMd() {
		return this.descriptionMd;
	}

	public CaptureSource getCaptureSource() {
		return this.captureSource;
	}

}
