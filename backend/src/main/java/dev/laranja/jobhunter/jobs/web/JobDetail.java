package dev.laranja.jobhunter.jobs.web;

import java.time.Instant;
import java.util.List;

import dev.laranja.jobhunter.jobs.JobView;

/** Detalhe da vaga com a candidatura (se houver) e a linha do tempo do funil. */
record JobDetail(JobView job, ApplicationSummary application, List<TimelineEvent> timeline) {

	record ApplicationSummary(long id, String status, Instant appliedAt, String channel, Long resumeVersionId,
			String resumeLabel, String notes, Instant nextActionAt) {
	}

	record TimelineEvent(long id, String fromStatus, String toStatus, String responseType, String origin, String note,
			Instant createdAt) {
	}

}
