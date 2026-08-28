package com.optiplant.inventory.iam.infrastructure.adapter.out.persistence;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserSpringDataRepository extends JpaRepository<UserJpaEntity, Long> {

	Optional<UserJpaEntity> findByUsername(String username);

	Optional<UserJpaEntity> findByExternalId(UUID externalId);

	/** Used by user-administration's duplicate-email check (create and edit). */
	Optional<UserJpaEntity> findByEmail(String email);

	/** Filtered, paginated listing for admin queries (user-administration "User
	 * query lists users..."). Plain JPQL, not native — unlike {@code
	 * AuditLogSpringDataRepository.search}'s {@code Instant} filters (slice 4
	 * deviation 4), every filter here is {@code Boolean}/{@code String}/{@code
	 * Long}, which Hibernate already gives the driver an explicit type for, so
	 * {@code Pageable}'s dynamic sort still works (rejected on a native query). */
	@Query("SELECT u FROM UserJpaEntity u WHERE (:active IS NULL OR u.active = :active) "
			+ "AND (:role IS NULL OR u.role = :role) AND (:branchId IS NULL OR u.branchId = :branchId)")
	Page<UserJpaEntity> search(@Param("active") Boolean active, @Param("role") String role,
			@Param("branchId") Long branchId, Pageable pageable);

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
