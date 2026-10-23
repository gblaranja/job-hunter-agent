package dev.laranja.jobhunter.resumes;

/** Uma versão nova do currículo foi enviada. A extração de texto e de skills escuta este evento. */
public record ResumeUploaded(long resumeVersionId) {
}
