package dev.laranja.jobhunter.jobs;

/** Uma vaga nova entrou no catálogo, coletada ou cadastrada à mão. A análise por IA escuta este evento. */
public record JobCollected(long jobId, JobOrigin origin) {
}
