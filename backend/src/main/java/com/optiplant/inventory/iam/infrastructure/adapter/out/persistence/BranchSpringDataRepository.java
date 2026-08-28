package com.optiplant.inventory.iam.infrastructure.adapter.out.persistence;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BranchSpringDataRepository extends JpaRepository<BranchJpaEntity, Long> {

	Optional<BranchJpaEntity> findByCode(String code);

	Optional<BranchJpaEntity> findByExternalId(UUID externalId);

	@Query(value = """
			SELECT b FROM BranchJpaEntity b
			WHERE (:active IS NULL OR b.active = :active)
			ORDER BY b.createdAt DESC
			""")
	Page<BranchJpaEntity> search(@Param("active") Boolean active, Pageable pageable);

	@Query(value = "SELECT id FROM branches WHERE external_id = :externalId", nativeQuery = true)
	Optional<Long> findIdByExternalId(@Param("externalId") UUID externalId);

	@Query(value = "SELECT external_id FROM branches WHERE id = :id", nativeQuery = true)
	Optional<UUID> findExternalIdById(@Param("id") Long id);
}
