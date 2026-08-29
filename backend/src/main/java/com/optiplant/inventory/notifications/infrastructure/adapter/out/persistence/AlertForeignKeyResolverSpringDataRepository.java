package com.optiplant.inventory.notifications.infrastructure.adapter.out.persistence;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/**
 * Resolves {@code external_id -> id} (and back) for {@code branches} and {@code users} —
 * {@code notifications} owns neither table and declares no {@code @Entity} for either, same
 * reasoning as {@code inventory}'s {@code ForeignKeyResolverSpringDataRepository}. Bound to
 * {@link SystemAlertJpaEntity} purely to satisfy Spring Data JPA's repository-factory
 * requirement for a registered entity type.
 */
public interface AlertForeignKeyResolverSpringDataRepository extends Repository<SystemAlertJpaEntity, Long> {

	@Query(value = "SELECT id FROM branches WHERE external_id = :externalId", nativeQuery = true)
	Optional<Long> findBranchIdByExternalId(@Param("externalId") UUID externalId);

	@Query(value = "SELECT external_id FROM branches WHERE id = :id", nativeQuery = true)
	Optional<UUID> findBranchExternalId(@Param("id") Long id);

	@Query(value = "SELECT id FROM users WHERE external_id = :externalId", nativeQuery = true)
	Optional<Long> findUserIdByExternalId(@Param("externalId") UUID externalId);

	@Query(value = "SELECT external_id FROM users WHERE id = :id", nativeQuery = true)
	Optional<UUID> findUserExternalId(@Param("id") Long id);
}
