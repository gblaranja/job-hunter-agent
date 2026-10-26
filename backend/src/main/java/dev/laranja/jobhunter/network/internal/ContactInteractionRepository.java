package dev.laranja.jobhunter.network.internal;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ContactInteractionRepository extends JpaRepository<ContactInteraction, Long> {

	boolean existsByEmailMessageId(long emailMessageId);

}
