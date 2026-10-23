package dev.laranja.jobhunter.profile.internal;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SearchProfileRepository extends JpaRepository<SearchProfile, Long> {

	List<SearchProfile> findAllByOrderByIdAsc();

	List<SearchProfile> findByActiveTrue();

}
