package com.optiplant.inventory.inventory.infrastructure.adapter.out.persistence;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/**
 * Resolves {@code external_id -> id} (and back) for {@code products}, {@code branches} and
 * {@code users} — the three tables {@code inventory} references by plain {@code Long} foreign
 * key without an {@code @Entity} of its own (design §6.1). No method here touches
 * {@code branch_inventories} or {@code kardex_movements}; the interface is bound to
 * {@link BranchInventoryJpaEntity} purely because Spring Data JPA's repository factory requires
 * a registered entity type to build the proxy — the same trick a repository with no natural
 * owning entity always needs.
 */
public interface ForeignKeyResolverSpringDataRepository extends Repository<BranchInventoryJpaEntity, Long> {

	@Query(value = "SELECT id FROM products WHERE external_id = :externalId", nativeQuery = true)
	Optional<Long> findProductIdByExternalId(@Param("externalId") UUID externalId);

	@Query(value = "SELECT id AS id, external_id AS externalId FROM products WHERE id IN (:ids)", nativeQuery = true)
	List<IdExternalIdRow> findProductExternalIds(@Param("ids") Collection<Long> ids);

	@Query(value = "SELECT external_id AS externalId, sku AS sku, name AS name FROM products WHERE external_id = :externalId",
			nativeQuery = true)
	Optional<ProductDescriptorRow> findProductDescriptor(@Param("externalId") UUID externalId);

	@Query(value = "SELECT external_id AS externalId, sku AS sku, name AS name FROM products WHERE external_id IN (:externalIds)",
			nativeQuery = true)
	List<ProductDescriptorRow> findProductDescriptors(@Param("externalIds") Collection<UUID> externalIds);

	@Query(value = "SELECT id FROM branches WHERE external_id = :externalId", nativeQuery = true)
	Optional<Long> findBranchIdByExternalId(@Param("externalId") UUID externalId);

	@Query(value = "SELECT id AS id, external_id AS externalId FROM branches WHERE id IN (:ids)", nativeQuery = true)
	List<IdExternalIdRow> findBranchExternalIds(@Param("ids") Collection<Long> ids);

	/** CU-INV-04 — every active branch, ordered by name (design §6.1). */
	@Query(value = "SELECT id AS id, external_id AS externalId, name AS name FROM branches WHERE is_active = TRUE ORDER BY name ASC",
			nativeQuery = true)
	List<ActiveBranchRow> findActiveBranches();

	@Query(value = "SELECT id FROM users WHERE external_id = :externalId", nativeQuery = true)
	Optional<Long> findUserIdByExternalId(@Param("externalId") UUID externalId);

	@Query(value = "SELECT id AS id, external_id AS externalId FROM users WHERE id IN (:ids)", nativeQuery = true)
	List<IdExternalIdRow> findUserExternalIds(@Param("ids") Collection<Long> ids);

	interface IdExternalIdRow {
		Long getId();

		UUID getExternalId();
	}

	interface ProductDescriptorRow {
		UUID getExternalId();

		String getSku();

		String getName();
	}

	interface ActiveBranchRow {
		Long getId();

		UUID getExternalId();

		String getName();
	}
}
