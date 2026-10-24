package dev.laranja.jobhunter.interviews;

import java.time.Instant;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import org.jspecify.annotations.Nullable;

@Schema(name = "StarStoryView")
public record StarStoryView(long id, String title, String situation, String task, String action, String result,
		@Nullable String reflection, List<String> skills, Instant createdAt, Instant updatedAt) {
}
