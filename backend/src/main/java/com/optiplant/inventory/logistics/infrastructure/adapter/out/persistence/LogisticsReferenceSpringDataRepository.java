package com.optiplant.inventory.logistics.infrastructure.adapter.out.persistence;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/**
 * Resolves {@code external_id -> id} (and back, with names) for {@code branches} — the only
 * table {@code logistics} references by plain {@code Long} foreign key without an {@code @Entity}
 * of its own (design §6.1, §6.2). Bound to {@link LogisticsRouteJpaEntity} purely because Spring
 * Data JPA's repository factory needs a registered entity type — exactly {@code inventory}'s
 * {@code ForeignKeyResolverSpringDataRepository}.
 */
public interface LogisticsReferenceSpringDataRepository extends Repository<LogisticsRouteJpaEntity, Long> {

	@Query(value = "SELECT id FROM branches WHERE external_id = :externalId AND is_active = TRUE", nativeQuery = true)
	Optional<Long> findActiveBranchIdByExternalId(@Param("externalId") UUID externalId);

	/** Any state — used to resolve the caller's own (session-trusted) branch, not to validate a new route. */
	@Query(value = "SELECT id FROM branches WHERE external_id = :externalId", nativeQuery = true)
	Optional<Long> findBranchIdByExternalId(@Param("externalId") UUID externalId);

	@Query(value = "SELECT id AS id, external_id AS externalId, name AS name FROM branches WHERE id = :id",
			nativeQuery = true)
	Optional<BranchDescriptorRow> findBranchDescriptor(@Param("id") Long id);

	@Query(value = "SELECT id AS id, external_id AS externalId, name AS name FROM branches WHERE id IN (:ids)",
			nativeQuery = true)
	List<BranchDescriptorRow> findBranchDescriptors(@Param("ids") Collection<Long> ids);

	interface BranchDescriptorRow {
		Long getId();

		UUID getExternalId();

		String getName();
	}
}
