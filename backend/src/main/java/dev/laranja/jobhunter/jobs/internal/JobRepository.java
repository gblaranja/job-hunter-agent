package dev.laranja.jobhunter.jobs.internal;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface JobRepository extends JpaRepository<Job, Long> {

	Optional<Job> findByFingerprint(String fingerprint);

}
