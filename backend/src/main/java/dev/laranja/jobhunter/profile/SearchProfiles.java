package dev.laranja.jobhunter.profile;

import java.time.Clock;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import dev.laranja.jobhunter.jobs.JobFamily;
import dev.laranja.jobhunter.profile.internal.SearchProfile;
import dev.laranja.jobhunter.profile.internal.SearchProfileRepository;
import dev.laranja.jobhunter.shared.NotFoundException;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Perfis de busca e o filtro que deriva deles. */
@Service
@Transactional
public class SearchProfiles {

	private final SearchProfileRepository profiles;

	private final ApplicationEventPublisher events;

	private final Clock clock;

	SearchProfiles(SearchProfileRepository profiles, ApplicationEventPublisher events, Clock clock) {
		this.profiles = profiles;
		this.events = events;
		this.clock = clock;
	}

	/**
	 * Exclusões de todos os perfis ativos; "só remoto" apenas se todos os perfis ativos pedirem isso. As famílias de
	 * cargo são a união das escolhidas, e um perfil ativo sem nenhuma escolhida aceita todas.
	 */
	@Transactional(readOnly = true)
	public JobFilter activeFilter() {
		List<SearchProfile> active = this.profiles.findByActiveTrue();
		if (active.isEmpty()) {
			return JobFilter.ACCEPT_ALL;
		}
		List<String> excluded = active.stream()
			.flatMap(profile -> profile.getExcludedKeywords().stream())
			.distinct()
			.toList();
		boolean remoteOnly = active.stream().allMatch(SearchProfile::isRemoteOnly);
		List<String> keywords = active.stream().flatMap(profile -> profile.getKeywords().stream()).distinct().toList();
		return new JobFilter(excluded, remoteOnly, keywords, acceptedFamilies(active));
	}

	private static Set<JobFamily> acceptedFamilies(List<SearchProfile> active) {
		if (active.isEmpty() || active.stream().anyMatch(profile -> profile.getJobFamilies().isEmpty())) {
			return Set.of();
		}
		Set<JobFamily> families = EnumSet.noneOf(JobFamily.class);
		active.forEach(profile -> families.addAll(profile.getJobFamilies()));
		return Set.copyOf(families);
	}

	@Transactional(readOnly = true)
	public List<SearchProfileView> list() {
		return this.profiles.findAllByOrderByIdAsc().stream().map(SearchProfiles::toView).toList();
	}

	public SearchProfileView create(SearchProfileCommand command) {
		Set<JobFamily> before = acceptedFamilies(this.profiles.findByActiveTrue());
		SearchProfileView created = toView(this.profiles.save(SearchProfile.create(command, this.clock.instant())));
		publishIfFamiliesChanged(before);
		return created;
	}

	public SearchProfileView update(long id, SearchProfileCommand command) {
		Set<JobFamily> before = acceptedFamilies(this.profiles.findByActiveTrue());
		SearchProfile profile = require(id);
		profile.apply(command);
		this.profiles.flush();
		publishIfFamiliesChanged(before);
		return toView(profile);
	}

	public void delete(long id) {
		Set<JobFamily> before = acceptedFamilies(this.profiles.findByActiveTrue());
		this.profiles.delete(require(id));
		this.profiles.flush();
		publishIfFamiliesChanged(before);
	}

	private void publishIfFamiliesChanged(Set<JobFamily> before) {
		Set<JobFamily> after = acceptedFamilies(this.profiles.findByActiveTrue());
		if (!after.equals(before)) {
			this.events.publishEvent(new JobFamiliesChanged(after));
		}
	}

	private SearchProfile require(long id) {
		return this.profiles.findById(id).orElseThrow(() -> NotFoundException.of("Perfil de busca", id));
	}

	private static SearchProfileView toView(SearchProfile profile) {
		return new SearchProfileView(profile.getId(), profile.getName(), profile.getKeywords(),
				profile.getExcludedKeywords(), profile.getLocations(), profile.isRemoteOnly(), profile.isActive(),
				profile.getJobFamilies());
	}

}
