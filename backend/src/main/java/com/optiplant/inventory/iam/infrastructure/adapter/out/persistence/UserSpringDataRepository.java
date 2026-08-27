package com.optiplant.inventory.iam.infrastructure.adapter.out.persistence;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserSpringDataRepository extends JpaRepository<UserJpaEntity, Long> {

	Optional<UserJpaEntity> findByUsername(String username);

	Optional<UserJpaEntity> findByExternalId(UUID externalId);

	@Query(value = "SELECT id FROM users WHERE external_id = :externalId", nativeQuery = true)
	Optional<Long> findIdByExternalId(@Param("externalId") UUID externalId);

	@Query(value = "SELECT external_id FROM users WHERE id = :id", nativeQuery = true)
	Optional<UUID> findExternalIdById(@Param("id") Long id);

	// No BranchJpaEntity exists yet (slice 5b); these three scalar reads are the
	// cheapest way to resolve a branch's external_id/is_active/internal id without
	// one. findBranchIdByExternalId is the forward direction the audit adapter
	// (slice 4) needs to resolve a branch external_id filter/context to its BIGINT
	// foreign key, mirroring findIdByExternalId above for users.
	@Query(value = "SELECT external_id FROM branches WHERE id = :branchId", nativeQuery = true)
	Optional<UUID> findBranchExternalId(@Param("branchId") Long branchId);

	@Query(value = "SELECT is_active FROM branches WHERE id = :branchId", nativeQuery = true)
	Optional<Boolean> findBranchActive(@Param("branchId") Long branchId);

	@Query(value = "SELECT id FROM branches WHERE external_id = :externalId", nativeQuery = true)
	Optional<Long> findBranchIdByExternalId(@Param("externalId") UUID externalId);
}
