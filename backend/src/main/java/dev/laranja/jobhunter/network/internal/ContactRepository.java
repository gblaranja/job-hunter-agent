package dev.laranja.jobhunter.network.internal;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ContactRepository extends JpaRepository<Contact, Long> {

	Optional<Contact> findByLinkedinUrl(String linkedinUrl);

	@Query("select c from Contact c where lower(c.email) = lower(:email)")
	Optional<Contact> findByEmail(String email);

}
