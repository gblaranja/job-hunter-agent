package dev.laranja.jobhunter.tracking.internal;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface JobSnapshotRepository extends JpaRepository<JobSnapshot, Long> {

	Optional<JobSnapshot> findByApplicationId(long applicationId);

	boolean existsByApplicationId(long applicationId);

}
