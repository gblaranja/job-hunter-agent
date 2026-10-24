package dev.laranja.jobhunter.tracking.web;

import java.time.Instant;
import java.util.List;

import dev.laranja.jobhunter.tracking.ApplicationChannel;
import dev.laranja.jobhunter.tracking.ApplicationStatus;
import dev.laranja.jobhunter.tracking.CaptureSource;
import dev.laranja.jobhunter.tracking.LinkStatus;
import dev.laranja.jobhunter.tracking.NextActionKind;
import io.swagger.v3.oas.annotations.media.Schema;
import org.jspecify.annotations.Nullable;

/** Respostas das telas de candidaturas. */
final class ApplicationViews {

	private ApplicationViews() {
	}

	@Schema(name = "CreatedApplication")
	record Created(long id) {
	}

	record Board(List<Column> columns) {

		@Schema(name = "BoardColumn")
		record Column(ApplicationStatus status, List<Card> cards) {
		}

		/**
		 * @param nextActionKind o que a próxima ação pede (follow-up, responder…)
		 */
		@Schema(name = "BoardCard")
		record Card(long applicationId, long jobId, String title, String company, ApplicationStatus status,
				long daysInStatus, @Nullable Long resumeVersionId, @Nullable String resumeLabel,
				@Nullable Instant nextActionAt, @Nullable NextActionKind nextActionKind,
				@Nullable LinkStatus linkStatus) {
		}

	}

	/**
	 * Linha da tela Minhas candidaturas.
	 * @param excerpt trecho da descrição salva em volta do termo buscado; {@code null} sem busca
	 */
	record AppliedListItem(long applicationId, long jobId, String title, String company, ApplicationStatus status,
			@Nullable Instant appliedAt, @Nullable ApplicationChannel channel, @Nullable String url,
			LinkStatus linkStatus, @Nullable Long resumeVersionId, @Nullable String resumeLabel,
			@Nullable String excerpt) {
	}

	/**
	 * Tudo o que o AppliedJobCard mostra.
	 * @param hasResponse a empresa já deu algum retorno; vaga removida sem retorno sugere posição preenchida
	 */
	record AppliedJob(long applicationId, long jobId, ApplicationStatus status, @Nullable Instant appliedAt,
			@Nullable ApplicationChannel channel, @Nullable Resume resume, Snapshot snapshot, boolean hasResponse) {

		@Schema(name = "AppliedJobResume")
		record Resume(long id, String label, String fileName) {
		}

		@Schema(name = "AppliedJobSnapshot")
		record Snapshot(@Nullable String url, LinkStatus linkStatus, @Nullable Instant linkCheckedAt, String title,
				String company, @Nullable String descriptionMd, CaptureSource captureSource, boolean hasPagePdf,
				Instant capturedAt) {
		}

	}

}
