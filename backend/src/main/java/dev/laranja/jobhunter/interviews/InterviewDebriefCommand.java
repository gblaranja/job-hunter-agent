package dev.laranja.jobhunter.interviews;

import java.time.LocalDate;
import java.util.List;

import dev.laranja.jobhunter.tracking.ApplicationStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.jspecify.annotations.Nullable;

/**
 * O que aconteceu na entrevista, registrado logo depois.
 *
 * @param stage ENTREVISTA ou TESTE_TECNICO
 * @param questions as perguntas que fizeram, uma por item
 * @param rating como você avalia a entrevista, de 1 a 5
 */
@Schema(name = "InterviewDebriefCommand")
public record InterviewDebriefCommand(@NotNull ApplicationStatus stage, @NotNull LocalDate interviewedOn,
		@Nullable @Size(max = 50) List<@Size(max = 500) String> questions, @Nullable @Size(max = 5000) String wentWell,
		@Nullable @Size(max = 5000) String toImprove, @Nullable @Min(1) @Max(5) Integer rating,
		@Nullable @Size(max = 5000) String nextSteps) {
}
