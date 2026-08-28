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
	 * query lists users..."). Native query with LEFT JOIN on branches eliminates
	 * N+1 round trips for branch external_id and active status. Ordering (most
	 * recent first) is fixed in SQL, matching AuditLogSpringDataRepository. */
	@Query(value = """
			SELECT u.external_id AS externalId,
			       u.username AS username,
			       u.email AS email,
			       u.password_hash AS passwordHash,
			       u.full_name AS fullName,
			       u.role AS role,
			       u.is_active AS active,
			       b.external_id AS branchExternalId,
			       b.is_active AS branchActive
			FROM users u
			LEFT JOIN branches b ON b.id = u.branch_id
			WHERE (:active IS NULL OR u.is_active = :active)
			  AND (:role IS NULL OR u.role = :role)
			  AND (:branchId IS NULL OR u.branch_id = :branchId)
			ORDER BY u.created_at DESC
			""",
			countQuery = """
					SELECT count(*) FROM users u
					WHERE (:active IS NULL OR u.is_active = :active)
					  AND (:role IS NULL OR u.role = :role)
					  AND (:branchId IS NULL OR u.branch_id = :branchId)
					""",
			nativeQuery = true)
	Page<UserSummaryProjection> search(@Param("active") Boolean active, @Param("role") String role,
			@Param("branchId") Long branchId, Pageable pageable);

	public interface UserSummaryProjection {
		UUID getExternalId();
		String getUsername();
		String getEmail();
		String getPasswordHash();
		String getFullName();
		String getRole();
		boolean isActive();
		UUID getBranchExternalId();
		Boolean getBranchActive();
	}

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
