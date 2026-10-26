package dev.laranja.jobhunter.agenda;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import org.jspecify.annotations.Nullable;

/**
 * O que pede ação hoje.
 *
 * @param jobs vagas para aplicar hoje: score a partir de 70, legitimidade ALTA ou MEDIA, sem eliminatório falho e
 * vistas nas últimas 72 h
 * @param followUps próximas ações vencidas nas candidaturas
 * @param emails e-mails que esperam a sua revisão
 * @param interviews entrevistas e testes técnicos em andamento, os da semana primeiro
 * @param tailoredResumes currículos adaptados prontos de vagas sem candidatura enviada
 */
public record TodayAgenda(LocalDate day, List<TodayJob> jobs, List<TodayFollowUp> followUps, List<TodayEmail> emails,
		List<TodayInterview> interviews, List<TodayTailoredResume> tailoredResumes, TodayWeeklyGoal weeklyGoal) {

	public record TodayJob(long id, String title, String company, int matchScore, Instant firstSeenAt,
			@Nullable String legitimacy, @Nullable String url) {
	}

	/** @param nextActionKind FOLLOW_UP, RESPONDER, ENTREVISTA ou OUTRO; nulo nas ações antigas */
	public record TodayFollowUp(long applicationId, long jobId, String title, String company, String status,
			Instant nextActionAt, @Nullable String nextActionKind, int followUpsSent) {
	}

	public record TodayEmail(long id, String sender, @Nullable String subject, Instant receivedAt,
			@Nullable String classification) {
	}

	public record TodayInterview(long applicationId, long jobId, String title, String company, String status,
			@Nullable Instant nextActionAt) {
	}

	public record TodayTailoredResume(long id, long jobId, String title, String company, Instant updatedAt) {
	}

	/**
	 * @param applied candidaturas enviadas desde segunda-feira
	 * @param aligned dessas, as aderentes (score a partir de 70 e sem eliminatório falho)
	 * @param goal meta semanal do perfil (padrão 12)
	 */
	public record TodayWeeklyGoal(LocalDate weekStart, int applied, int aligned, int goal) {
	}

}
