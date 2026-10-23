package dev.laranja.jobhunter.jobs;

/** Resultado de uma vaga coletada: nova no catálogo ou já conhecida (só atualiza o "visto por último"). */
public record IngestResult(long jobId, boolean created) {
}
