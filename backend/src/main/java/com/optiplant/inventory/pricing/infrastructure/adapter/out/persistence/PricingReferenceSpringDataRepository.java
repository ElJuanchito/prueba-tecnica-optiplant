package com.optiplant.inventory.pricing.infrastructure.adapter.out.persistence;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/**
 * Resolves {@code external_id -> id} (and back) for {@code products}, {@code branches} and
 * {@code price_lists} references via native queries (design §6.1). Bound to
 * {@link PriceListJpaEntity} because Spring Data JPA needs a registered entity type.
 */
public interface PricingReferenceSpringDataRepository extends Repository<PriceListJpaEntity, Long> {

	@Query(value = "SELECT id FROM products WHERE external_id = :externalId AND is_active = TRUE", nativeQuery = true)
	Optional<Long> findActiveProductIdByExternalId(@Param("externalId") UUID externalId);

	@Query(value = "SELECT id FROM branches WHERE external_id = :externalId AND is_active = TRUE", nativeQuery = true)
	Optional<Long> findActiveBranchIdByExternalId(@Param("externalId") UUID externalId);

	@Query(value = "SELECT id FROM price_lists WHERE external_id = :externalId", nativeQuery = true)
	Optional<Long> findPriceListIdByExternalId(@Param("externalId") UUID externalId);

	@Query(value = """
			SELECT pl.external_id FROM branches b
			JOIN price_lists pl ON pl.id = b.default_price_list_id
			WHERE b.external_id = :branchExternalId AND pl.is_active = TRUE
			""", nativeQuery = true)
	Optional<UUID> findDefaultPriceListExternalIdForBranch(@Param("branchExternalId") UUID branchExternalId);

	@Query(value = "SELECT id AS id, external_id AS externalId FROM products WHERE id IN (:ids)", nativeQuery = true)
	List<IdExternalIdRow> findProductExternalIds(@Param("ids") Collection<Long> ids);

	@Query(value = "SELECT id AS id, external_id AS externalId FROM branches WHERE id IN (:ids)", nativeQuery = true)
	List<IdExternalIdRow> findBranchExternalIds(@Param("ids") Collection<Long> ids);

	@Query(value = "SELECT id AS id, external_id AS externalId FROM price_lists WHERE id IN (:ids)", nativeQuery = true)
	List<IdExternalIdRow> findPriceListExternalIds(@Param("ids") Collection<Long> ids);

	@Query(value = "SELECT id AS id, external_id AS externalId FROM products WHERE external_id IN (:externalIds)", nativeQuery = true)
	List<IdExternalIdRow> findProductIds(@Param("externalIds") Collection<UUID> externalIds);

	interface IdExternalIdRow {
		Long getId();

		UUID getExternalId();
	}
}
