package dev.laranja.jobhunter.recognition;

/** O selo vigente da empresa pode ter mudado (verificação, confirmação, rejeição ou importação). O fit escuta. */
public record RecognitionChanged(long companyId) {
}
