package dev.laranja.jobhunter.jobs.internal;

import dev.laranja.jobhunter.jobs.JobFamily;
import dev.laranja.jobhunter.jobs.internal.JobScopeDetector.InternationalTerms;
import org.jspecify.annotations.Nullable;

/**
 * Classificação da vaga pelas regras: a família de cargo (pelo título) e as condições de contratação no exterior.
 * Gravada em {@code job} na entrada da vaga; {@link #VERSION} sobe quando as regras mudam, e o backfill da subida
 * reclassifica as vagas de versões anteriores.
 */
public record JobClassification(@Nullable JobFamily family, InternationalTerms international) {

	public static final int VERSION = 1;

	public static JobClassification of(@Nullable String title, @Nullable String location,
			@Nullable String description) {
		return new JobClassification(JobFamilyClassifier.classify(title),
				JobScopeDetector.international(title, location, description));
	}

	public @Nullable String familyName() {
		return (this.family != null) ? this.family.name() : null;
	}

}
