package dev.laranja.jobhunter.recognition;

/** Um selo encontrado precisa da sua confirmação (confiança baixa ou homônimo). Vira notificação. */
public record RecognitionReviewNeeded(long recognitionId, long companyId, String companyName, RecognitionLevel level,
		int year) {
}
