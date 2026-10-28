package dev.laranja.jobhunter.profile;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import org.jspecify.annotations.Nullable;

/**
 * Dados da pessoa candidata para a triagem, com a sugestão lida do currículo para ela confirmar.
 *
 * @param acceptedContracts regimes aceitos: CLT, PJ ou CONTRACTOR
 * @param workAuthorizations países (ISO de duas letras) onde a pessoa pode trabalhar
 * @param desiredSalaryClt pretensão mensal bruta em CLT, em reais
 * @param desiredSalaryPj pretensão mensal em PJ, em reais
 * @param weeklyGoal meta de candidaturas aderentes por semana
 * @param suggestedFrom rótulo do currículo de onde veio a sugestão
 */
public record CandidateProfileView(@Nullable Integer yearsOfExperience, @Nullable EnglishLevel englishLevel,
		@Nullable String city, @Nullable String state, @Nullable Integer maxOnsiteDays, List<String> acceptedContracts,
		List<String> workAuthorizations, @Nullable EducationLevel educationLevel, @Nullable String education,
		List<String> certifications, @Nullable BigDecimal desiredSalaryClt, @Nullable BigDecimal desiredSalaryPj,
		int weeklyGoal, @Nullable Integer suggestedYearsOfExperience, @Nullable EnglishLevel suggestedEnglishLevel,
		@Nullable String suggestedFrom, @Nullable Instant updatedAt) {

	public static final int DEFAULT_WEEKLY_GOAL = 12;

	/** Perfil ainda não preenchido. */
	public static CandidateProfileView empty() {
		return new CandidateProfileView(null, null, null, null, null, List.of("CLT", "PJ"), List.of("BR"), null, null,
				List.of(), null, null, DEFAULT_WEEKLY_GOAL, null, null, null, null);
	}

}
