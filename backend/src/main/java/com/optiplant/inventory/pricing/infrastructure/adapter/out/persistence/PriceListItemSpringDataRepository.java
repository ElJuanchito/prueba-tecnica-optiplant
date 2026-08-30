package com.optiplant.inventory.pricing.infrastructure.adapter.out.persistence;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Spring Data JPA repository for {@link PriceListItemJpaEntity} (design §6.1, §6.2).
 */
public interface PriceListItemSpringDataRepository extends JpaRepository<PriceListItemJpaEntity, Long> {

	Optional<PriceListItemJpaEntity> findByExternalId(UUID externalId);

	@Query(value = """
			SELECT * FROM price_list_items
			WHERE price_list_id = :priceListId
			  AND product_id = :productId
			  AND ((:branchId IS NULL AND branch_id IS NULL) OR branch_id = :branchId)
			  AND valid_to IS NULL
			ORDER BY id ASC
			""", nativeQuery = true)
	List<PriceListItemJpaEntity> findOpen(@Param("priceListId") Long priceListId,
			@Param("productId") Long productId, @Param("branchId") Long branchId);

	/**
	 * Superset query over {@code idx_price_list_items_lookup} (design §6.2, RNF-PER-02).
	 * Filtering by date is done in SQL; branch-over-corporate preference is folded in domain.
	 */
	@Query(value = """
			SELECT * FROM price_list_items
			WHERE price_list_id = :priceListId
			  AND product_id IN (:productIds)
			  AND (branch_id = :branchId OR branch_id IS NULL)
			  AND valid_from <= :date
			  AND (valid_to IS NULL OR valid_to >= :date)
			""", nativeQuery = true)
	List<PriceListItemJpaEntity> findEligible(@Param("priceListId") Long priceListId,
			@Param("branchId") Long branchId, @Param("productIds") Collection<Long> productIds,
			@Param("date") LocalDate date);

	@Query(value = """
			SELECT * FROM price_list_items
			WHERE price_list_id = :priceListId
			  AND (:productId IS NULL OR product_id = :productId)
			  AND (:branchId IS NULL OR branch_id = :branchId)
			  AND (:currentOnly IS NULL OR :currentOnly = FALSE OR (valid_from <= :today AND (valid_to IS NULL OR valid_to >= :today)))
			ORDER BY valid_from DESC, id DESC
			""", countQuery = """
			SELECT count(*) FROM price_list_items
			WHERE price_list_id = :priceListId
			  AND (:productId IS NULL OR product_id = :productId)
			  AND (:branchId IS NULL OR branch_id = :branchId)
			  AND (:currentOnly IS NULL OR :currentOnly = FALSE OR (valid_from <= :today AND (valid_to IS NULL OR valid_to >= :today)))
			""", nativeQuery = true)
	Page<PriceListItemJpaEntity> search(@Param("priceListId") Long priceListId,
			@Param("productId") Long productId, @Param("branchId") Long branchId,
			@Param("currentOnly") Boolean currentOnly, @Param("today") LocalDate today, Pageable pageable);
}
