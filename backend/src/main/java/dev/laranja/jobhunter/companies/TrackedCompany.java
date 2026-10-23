package dev.laranja.jobhunter.companies;

/** Empresa monitorada ou alvo com o ATS público configurado, consultada diariamente pela coleta. */
public record TrackedCompany(long id, String name, String atsType, String atsSlug) {
}
