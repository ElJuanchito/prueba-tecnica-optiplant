package com.optiplant.inventory.inventory.infrastructure.adapter.out.persistence;

import com.optiplant.inventory.shared.stock.StockMovementType;
import java.time.Instant;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Spring Data repository for {@code kardex_movements} (design §6.1). Query methods only — no
 * {@code delete*}, no {@code @Modifying} update (task 2.3, R-17, RNF-INT-02): the append-only
 * guarantee is a property of the methods this interface declares, not only of the port above it.
 */
public interface KardexMovementSpringDataRepository extends JpaRepository<KardexMovementJpaEntity, Long> {

	/** No lock, ordered {@code created_at} ascending, fixed in JPQL (T-05, R-16). */
	@Query(value = """
			SELECT k FROM KardexMovementJpaEntity k
			WHERE (:branchId IS NULL OR k.branchId = :branchId)
			  AND (:productId IS NULL OR k.productId = :productId)
			  AND (:movementType IS NULL OR k.movementType = :movementType)
			  AND (:from IS NULL OR k.createdAt >= :from)
			  AND (:to IS NULL OR k.createdAt <= :to)
			ORDER BY k.createdAt ASC
			""", countQuery = """
			SELECT COUNT(k) FROM KardexMovementJpaEntity k
			WHERE (:branchId IS NULL OR k.branchId = :branchId)
			  AND (:productId IS NULL OR k.productId = :productId)
			  AND (:movementType IS NULL OR k.movementType = :movementType)
			  AND (:from IS NULL OR k.createdAt >= :from)
			  AND (:to IS NULL OR k.createdAt <= :to)
			""")
	Page<KardexMovementJpaEntity> search(@Param("branchId") Long branchId, @Param("productId") Long productId,
			@Param("movementType") StockMovementType movementType, @Param("from") Instant from,
			@Param("to") Instant to, Pageable pageable);

	/** The second clause of {@code ProductStockPresencePort}'s predicate — any branch, ever. */
	boolean existsByProductId(Long productId);
}
