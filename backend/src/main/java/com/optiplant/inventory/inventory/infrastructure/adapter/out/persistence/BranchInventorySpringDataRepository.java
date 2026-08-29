package com.optiplant.inventory.inventory.infrastructure.adapter.out.persistence;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Spring Data repository for {@code branch_inventories} (design §6.1, §7).
 *
 * <p>{@link #findByBranchIdAndProductId} is a derived query annotated {@link Lock}
 * ({@link LockModeType#PESSIMISTIC_WRITE}) → {@code SELECT ... FOR UPDATE} (T-02, design §6.1
 * verbatim). No {@code @QueryHints} lock timeout is configured: verified against
 * {@code hibernate-core-7.4.5.Final}, {@code PostgreSQLDialect} renders only
 * {@code for update}/{@code for update nowait}/{@code for update skip locked} — a numeric
 * {@code jakarta.persistence.lock.timeout} has no PostgreSQL rendering and would be silently
 * dropped (design §7). Any lock failure surfaces as {@code PessimisticLockingFailureException},
 * mapped by {@code InventoryExceptionHandler} to {@code 409 concurrent_stock_update}.
 */
public interface BranchInventorySpringDataRepository extends JpaRepository<BranchInventoryJpaEntity, Long> {

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	Optional<BranchInventoryJpaEntity> findByBranchIdAndProductId(Long branchId, Long productId);

	/** No lock (T-05) — {@code save} re-fetches the already-locked row by its own {@code external_id}. */
	Optional<BranchInventoryJpaEntity> findByExternalId(UUID externalId);

	@Query(value = """
			SELECT b FROM BranchInventoryJpaEntity b
			WHERE b.branchId = :branchId
			  AND (:productId IS NULL OR b.productId = :productId)
			  AND (:belowThresholdOnly = FALSE OR b.currentStock <= b.minStockThreshold)
			""", countQuery = """
			SELECT COUNT(b) FROM BranchInventoryJpaEntity b
			WHERE b.branchId = :branchId
			  AND (:productId IS NULL OR b.productId = :productId)
			  AND (:belowThresholdOnly = FALSE OR b.currentStock <= b.minStockThreshold)
			""")
	Page<BranchInventoryJpaEntity> search(@Param("branchId") Long branchId, @Param("productId") Long productId,
			@Param("belowThresholdOnly") boolean belowThresholdOnly, Pageable pageable);

	/** CU-INV-04 — every row of {@code productId} across the given (active) branch ids. */
	List<BranchInventoryJpaEntity> findByProductIdAndBranchIdIn(Long productId, List<Long> branchIds);

	@Query("SELECT COUNT(b) > 0 FROM BranchInventoryJpaEntity b WHERE b.productId = :productId "
			+ "AND (b.currentStock <> 0 OR b.reservedStock <> 0 OR b.inTransitStock <> 0)")
	boolean existsAnyNonZeroBalance(@Param("productId") Long productId);
}
