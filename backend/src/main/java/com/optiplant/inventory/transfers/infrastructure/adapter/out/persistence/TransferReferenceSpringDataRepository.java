package com.optiplant.inventory.transfers.infrastructure.adapter.out.persistence;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/**
 * Resolves {@code external_id -> id} (and back) for {@code branches}, {@code products} and
 * {@code users} — the three tables {@code transfers} references by plain {@code Long} foreign
 * key without an {@code @Entity} of its own (design §6.1). Bound to {@link TransferJpaEntity}
 * purely because Spring Data JPA's repository factory needs a registered entity type — exactly
 * {@code inventory}'s {@code ForeignKeyResolverSpringDataRepository}.
 */
public interface TransferReferenceSpringDataRepository extends Repository<TransferJpaEntity, Long> {

	/** Active only (R-03, {@code requireActiveBranch}). */
	@Query(value = "SELECT id FROM branches WHERE external_id = :externalId AND is_active = TRUE", nativeQuery = true)
	Optional<Long> findActiveBranchIdByExternalId(@Param("externalId") UUID externalId);

	/** Active only (R-03 — a product resolved by a transfer request must be active). */
	@Query(value = "SELECT id FROM products WHERE external_id = :externalId AND is_active = TRUE", nativeQuery = true)
	Optional<Long> findActiveProductIdByExternalId(@Param("externalId") UUID externalId);

	@Query(value = "SELECT id FROM users WHERE external_id = :externalId", nativeQuery = true)
	Optional<Long> findUserIdByExternalId(@Param("externalId") UUID externalId);

	/** Active only, with {@code sku}/{@code name} — backs {@code TransferReferencePort#findProduct}. */
	@Query(value = "SELECT external_id AS externalId, sku AS sku, name AS name FROM products "
			+ "WHERE external_id = :externalId AND is_active = TRUE", nativeQuery = true)
	Optional<ProductDescriptorRow> findActiveProductDescriptor(@Param("externalId") UUID externalId);

	/**
	 * Any state — a page or detail must still name a product that was later disabled
	 * (audit fidelity), not just the active ones {@link #findActiveProductDescriptor} enforces at
	 * write time.
	 */
	@Query(value = "SELECT external_id AS externalId, sku AS sku, name AS name FROM products "
			+ "WHERE external_id IN (:externalIds)", nativeQuery = true)
	List<ProductDescriptorRow> findProductDescriptors(@Param("externalIds") Collection<UUID> externalIds);

	@Query(value = "SELECT external_id AS externalId, name AS name FROM branches WHERE external_id IN (:externalIds)",
			nativeQuery = true)
	List<BranchDescriptorRow> findBranchDescriptors(@Param("externalIds") Collection<UUID> externalIds);

	@Query(value = "SELECT id AS id, external_id AS externalId FROM products WHERE id IN (:ids)", nativeQuery = true)
	List<IdExternalIdRow> findProductExternalIds(@Param("ids") Collection<Long> ids);

	@Query(value = "SELECT id AS id, external_id AS externalId FROM branches WHERE id IN (:ids)", nativeQuery = true)
	List<IdExternalIdRow> findBranchExternalIds(@Param("ids") Collection<Long> ids);

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

	interface BranchDescriptorRow {
		UUID getExternalId();

		String getName();
	}
}
