package dev.laranja.jobhunter.tracking.internal;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ApplicationRepository extends JpaRepository<Application, Long> {

	Optional<Application> findByJobId(long jobId);

}
