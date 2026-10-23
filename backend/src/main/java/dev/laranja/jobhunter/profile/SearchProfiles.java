package dev.laranja.jobhunter.profile;

import java.time.Clock;
import java.util.List;

import dev.laranja.jobhunter.profile.internal.SearchProfile;
import dev.laranja.jobhunter.profile.internal.SearchProfileRepository;
import dev.laranja.jobhunter.shared.NotFoundException;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Perfis de busca e o filtro que deriva deles. */
@Service
@Transactional
public class SearchProfiles {

	private final SearchProfileRepository profiles;

	private final Clock clock;

	SearchProfiles(SearchProfileRepository profiles, Clock clock) {
		this.profiles = profiles;
		this.clock = clock;
	}

	/** Exclusões de todos os perfis ativos; "só remoto" apenas se todos os perfis ativos pedirem isso. */
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
		return new JobFilter(excluded, remoteOnly, keywords);
	}

	@Transactional(readOnly = true)
	public List<SearchProfileView> list() {
		return this.profiles.findAllByOrderByIdAsc().stream().map(SearchProfiles::toView).toList();
	}

	public SearchProfileView create(SearchProfileCommand command) {
		return toView(this.profiles.save(SearchProfile.create(command, this.clock.instant())));
	}

	public SearchProfileView update(long id, SearchProfileCommand command) {
		SearchProfile profile = require(id);
		profile.apply(command);
		return toView(profile);
	}

	public void delete(long id) {
		this.profiles.delete(require(id));
	}

	private SearchProfile require(long id) {
		return this.profiles.findById(id).orElseThrow(() -> NotFoundException.of("Perfil de busca", id));
	}

	private static SearchProfileView toView(SearchProfile profile) {
		return new SearchProfileView(profile.getId(), profile.getName(), profile.getKeywords(),
				profile.getExcludedKeywords(), profile.getLocations(), profile.isRemoteOnly(), profile.isActive());
	}

}
