package dev.laranja.jobhunter.jobs;

/**
 * Se a vaga é no Brasil ou no exterior. A caixa de vagas separa as duas, e as nacionais vêm primeiro.
 * Sem informação explícita, uma vaga com a descrição em inglês é tratada como internacional.
 */
public enum JobScope {

	NACIONAL, INTERNACIONAL

}
