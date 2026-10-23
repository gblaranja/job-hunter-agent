package dev.laranja.jobhunter.tracking.web;

import java.time.Instant;
import java.util.List;

import dev.laranja.jobhunter.tracking.ApplicationChannel;
import dev.laranja.jobhunter.tracking.ApplicationStatus;
import dev.laranja.jobhunter.tracking.CaptureSource;
import dev.laranja.jobhunter.tracking.LinkStatus;

/** Respostas das telas de candidaturas. */
final class ApplicationViews {

	private ApplicationViews() {
	}

	record Created(long id) {
	}

	record Board(List<Column> columns) {

		record Column(ApplicationStatus status, List<Card> cards) {
		}

		record Card(long applicationId, long jobId, String title, String company, ApplicationStatus status,
				long daysInStatus, Long resumeVersionId, String resumeLabel, Instant nextActionAt,
				LinkStatus linkStatus) {
		}

	}

	/**
	 * Linha da tela Minhas candidaturas.
	 * @param excerpt trecho da descrição salva em volta do termo buscado; {@code null} sem busca
	 */
	record AppliedListItem(long applicationId, long jobId, String title, String company, ApplicationStatus status,
			Instant appliedAt, ApplicationChannel channel, String url, LinkStatus linkStatus, Long resumeVersionId,
			String resumeLabel, String excerpt) {
	}

	/** Tudo o que o AppliedJobCard mostra. */
	record AppliedJob(long applicationId, long jobId, ApplicationStatus status, Instant appliedAt,
			ApplicationChannel channel, Resume resume, Snapshot snapshot) {

		record Resume(long id, String label, String fileName) {
		}

		record Snapshot(String url, LinkStatus linkStatus, Instant linkCheckedAt, String title, String company,
				String descriptionMd, CaptureSource captureSource, boolean hasPagePdf, Instant capturedAt) {
		}

	}

}
