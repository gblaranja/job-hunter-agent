package dev.laranja.jobhunter.profile;

import java.math.BigDecimal;
import java.util.List;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.jspecify.annotations.Nullable;

/**
 * Substitui os dados da triagem. Campos nulos ficam sem informação (o eliminatório correspondente vira "sem
 * informação"); listas nulas viram vazias, e a meta semanal nula volta ao padrão.
 *
 * @param acceptedContracts regimes aceitos: CLT, PJ ou CONTRACTOR
 * @param workAuthorizations países (código ISO de duas letras) onde a pessoa pode trabalhar
 * @param desiredSalaryClt pretensão mensal bruta em CLT, em reais
 * @param desiredSalaryPj pretensão mensal em PJ, em reais
 */
public record CandidateProfileCommand(@Nullable @Min(0) @Max(60) Integer yearsOfExperience,
		@Nullable EnglishLevel englishLevel, @Nullable @Size(max = 120) String city,
		@Nullable @Pattern(regexp = "[A-Za-z]{2}") String state, @Nullable @Min(0) @Max(5) Integer maxOnsiteDays,
		@Nullable List<@Pattern(regexp = "(?i)(CLT|PJ|CONTRACTOR)") String> acceptedContracts,
		@Nullable List<@Pattern(regexp = "[A-Za-z]{2}") String> workAuthorizations,
		@Nullable EducationLevel educationLevel, @Nullable @Size(max = 300) String education,
		@Nullable List<@Size(max = 120) String> certifications,
		@Nullable @DecimalMin("0") BigDecimal desiredSalaryClt, @Nullable @DecimalMin("0") BigDecimal desiredSalaryPj,
		@Nullable @Min(1) @Max(100) Integer weeklyGoal) {
}
