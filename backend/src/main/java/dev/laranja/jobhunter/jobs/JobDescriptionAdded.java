package dev.laranja.jobhunter.jobs;

/** Uma vaga que entrou sem descrição (alerta do LinkedIn) ganhou a descrição completa. A análise é refeita. */
public record JobDescriptionAdded(long jobId) {
}
