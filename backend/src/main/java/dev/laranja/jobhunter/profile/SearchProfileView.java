package dev.laranja.jobhunter.profile;

import java.util.List;

import dev.laranja.jobhunter.jobs.JobFamily;

public record SearchProfileView(long id, String name, List<String> keywords, List<String> excludedKeywords,
		List<String> locations, boolean remoteOnly, boolean active, List<JobFamily> jobFamilies) {
}
