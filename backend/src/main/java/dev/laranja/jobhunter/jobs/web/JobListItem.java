package dev.laranja.jobhunter.jobs.web;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import dev.laranja.jobhunter.jobs.FeedbackRating;
import dev.laranja.jobhunter.jobs.FeedbackReason;
import dev.laranja.jobhunter.jobs.InternationalEligibility;
import dev.laranja.jobhunter.jobs.JobFamily;
import dev.laranja.jobhunter.jobs.JobOrigin;
import dev.laranja.jobhunter.jobs.JobScope;
import dev.laranja.jobhunter.jobs.Legitimacy;
import dev.laranja.jobhunter.jobs.WorkModel;
import org.jspecify.annotations.Nullable;

/**
 * Linha da caixa de vagas.
 *
 * @param scope nacional ou internacional; a caixa de vagas mostra cada um numa seção
 * @param status status da candidatura, ou {@code NOVA} quando a vaga ainda não tem candidatura
 * @param preview começo da descrição em texto corrido
 * @param matchScore aderência ao currículo padrão (0 a 100); nulo antes da análise ou sem currículo
 * @param gptwLevel selo GPTW vigente da empresa (RANKING_TI, RANKING ou CERTIFICADA)
 * @param scoreWithBonus pontuação da ordenação da caixa: aderência mais o bônus GPTW (+10, +7 ou +4) e o de frescor
 * (até +10, caindo a zero em 72 h), menos 5 pontos para legitimidade MEDIA
 * @param firstSeenAt primeira vez que a vaga foi vista em qualquer fonte, para mostrar "há 5 h"
 * @param legitimacy chance de a vaga ser real e estar aberta; nula antes da primeira avaliação
 * @param legitimacyReasons motivos da legitimidade, em frases curtas
 * @param failedKnockouts eliminatórios da vaga que a pessoa não atende
 * @param connections contatos e conexões do LinkedIn na empresa da vaga
 * @param salaryEquivalent equivalente anual em reais do salário da vaga (o maior valor da faixa): CLT com 13,33
 * salários, FGTS e benefícios; PJ líquido; nulo sem salário ou sem câmbio
 * @param belowSalaryFloor a vaga paga menos que a sua pretensão no regime dela; nulo quando não dá para saber
 * @param jobFamily família de cargo; nula quando não reconhecida
 * @param internationalEligibility de onde a pessoa pode ser contratada (vagas do exterior)
 * @param requiredTimezone fuso exigido, como aparece na vaga
 * @param contractor contratação como contractor
 */
record JobListItem(long id, String title, String company, @Nullable Long companyId, @Nullable String location,
		WorkModel workModel, JobScope scope, @Nullable String url, JobOrigin origin, Instant collectedAt,
		@Nullable String preview, @Nullable Long applicationId, String status, @Nullable FeedbackRating rating, List<FeedbackReason> reasons,
		@Nullable Integer matchScore, @Nullable String summary, List<String> matchedSkills, List<String> gaps,
		@Nullable String gptwLevel, @Nullable Integer scoreWithBonus, int failedKnockouts, Instant firstSeenAt,
		@Nullable Legitimacy legitimacy, List<String> legitimacyReasons, int connections,
		@Nullable BigDecimal salaryEquivalent, @Nullable Boolean belowSalaryFloor, @Nullable JobFamily jobFamily,
		@Nullable InternationalEligibility internationalEligibility, @Nullable String requiredTimezone,
		boolean contractor) {
}
