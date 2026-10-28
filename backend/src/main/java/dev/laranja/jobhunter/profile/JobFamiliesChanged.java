package dev.laranja.jobhunter.profile;

import java.util.Set;

import dev.laranja.jobhunter.jobs.JobFamily;

/**
 * As famílias de cargo aceitas pelos perfis de busca ativos mudaram.
 *
 * @param accepted famílias aceitas agora; vazio aceita todas
 */
public record JobFamiliesChanged(Set<JobFamily> accepted) {
}
