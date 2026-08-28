package com.optiplant.inventory.iam.infrastructure.adapter.out.persistence;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RefreshTokenSpringDataRepository extends JpaRepository<RefreshTokenJpaEntity, Long> {

	Optional<RefreshTokenJpaEntity> findByTokenHash(String tokenHash);

	/** Revokes exactly one still-live session by its {@code external_id}. A no-op
	 * (0 rows) if it is already revoked — {@code LogoutService}/{@code
	 * SessionRefreshService} treat that as idempotent, not an error. */
	@Modifying
	@Query("UPDATE RefreshTokenJpaEntity r SET r.revokedAt = :revokedAt, r.revokedReason = :reason "
			+ "WHERE r.externalId = :externalId AND r.revokedAt IS NULL")
	int revokeByExternalId(@Param("externalId") UUID externalId, @Param("revokedAt") Instant revokedAt,
			@Param("reason") String reason);

	/** Revokes every still-live session sharing {@code family_id} — reuse detection
	 * (design decision: revoke the family, not the whole user; P4 keeps other
	 * devices/families alive). */
	@Modifying
	@Query("UPDATE RefreshTokenJpaEntity r SET r.revokedAt = :revokedAt, r.revokedReason = :reason "
			+ "WHERE r.familyId = :familyId AND r.revokedAt IS NULL")
	int revokeByFamilyId(@Param("familyId") UUID familyId, @Param("revokedAt") Instant revokedAt,
			@Param("reason") String reason);

	/** Revokes every still-live session of one user, across every {@code
	 * family_id} — user-administration's disable flow (P2/P4), unlike {@link
	 * #revokeByFamilyId}'s single-family scope for reuse detection. */
	@Modifying
	@Query("UPDATE RefreshTokenJpaEntity r SET r.revokedAt = :revokedAt, r.revokedReason = :reason "
			+ "WHERE r.userId = :userId AND r.revokedAt IS NULL")
	int revokeByUserId(@Param("userId") Long userId, @Param("revokedAt") Instant revokedAt,
			@Param("reason") String reason);
}
