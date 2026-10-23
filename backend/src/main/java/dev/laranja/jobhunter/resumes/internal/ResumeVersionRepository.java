package dev.laranja.jobhunter.resumes.internal;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface ResumeVersionRepository extends JpaRepository<ResumeVersion, Long> {

	Optional<ResumeVersion> findBySha256(String sha256);

	boolean existsByDefaultVersionTrue();

	@Modifying(flushAutomatically = true)
	@Query("update ResumeVersion r set r.defaultVersion = false where r.defaultVersion = true and r.id <> :keepId")
	void clearDefaultExcept(long keepId);

}
