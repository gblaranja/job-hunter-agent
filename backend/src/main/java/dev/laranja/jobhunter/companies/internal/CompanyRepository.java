package dev.laranja.jobhunter.companies.internal;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface CompanyRepository extends JpaRepository<Company, Long> {

	Optional<Company> findByNormalizedName(String normalizedName);

	@Query(value = """
			select c.* from company c
			join company_alias a on a.company_id = c.id
			where a.alias = :alias
			""", nativeQuery = true)
	Optional<Company> findByAlias(String alias);

}
