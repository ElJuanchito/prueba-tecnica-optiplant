package com.optiplant.inventory.inventory.infrastructure.adapter.out.persistence;

import java.time.Instant;
import java.util.List;
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

	/**
	 * No lock, ordered {@code created_at} ascending (T-05, R-16).
	 *
	 * <p><strong>Native, not JPQL</strong> (found by executing, not by reading — CLAUDE.md): a plain
	 * JPQL {@code (:from IS NULL OR k.createdAt >= :from)} makes PostgreSQL's extended query
	 * protocol fail with "could not determine data type of parameter" for the {@link Instant}
	 * params — Hibernate gives the driver no explicit SQL type hint for a parameter whose only
	 * occurrence is an {@code IS NULL} check, so an explicit {@code ::timestamptz} cast is
	 * required. Exact same defect and fix as {@code AuditLogSpringDataRepository.search}
	 * (tasks.md 3.1/R-19 regression found while writing {@code InventoryBranchIsolationIT}).
	 * {@code movementType} is bound as a plain {@code String} for the same reason — no ambiguous
	 * enum type to infer.
	 */
	@Query(value = """
			SELECT * FROM kardex_movements
			WHERE (:branchId IS NULL OR branch_id = :branchId)
			  AND (:productId IS NULL OR product_id = :productId)
			  AND (:movementType IS NULL OR movement_type = :movementType)
			  AND (CAST(:from AS timestamptz) IS NULL OR created_at >= CAST(:from AS timestamptz))
			  AND (CAST(:to AS timestamptz) IS NULL OR created_at <= CAST(:to AS timestamptz))
			ORDER BY created_at ASC
			""", countQuery = """
			SELECT count(*) FROM kardex_movements
			WHERE (:branchId IS NULL OR branch_id = :branchId)
			  AND (:productId IS NULL OR product_id = :productId)
			  AND (:movementType IS NULL OR movement_type = :movementType)
			  AND (CAST(:from AS timestamptz) IS NULL OR created_at >= CAST(:from AS timestamptz))
			  AND (CAST(:to AS timestamptz) IS NULL OR created_at <= CAST(:to AS timestamptz))
			""", nativeQuery = true)
	Page<KardexMovementJpaEntity> search(@Param("branchId") Long branchId, @Param("productId") Long productId,
			@Param("movementType") String movementType, @Param("from") Instant from, @Param("to") Instant to,
			Pageable pageable);

	/** The second clause of {@code ProductStockPresencePort}'s predicate — any branch, ever. */
	boolean existsByProductId(Long productId);

	/**
	 * D-2, {@code OutboundValuationPort} — the unit cost stamped on every {@code TRANSFER_OUT}
	 * (or other outbound movement) carrying this exact Kardex reference, keyed by product. Access
	 * path is {@code idx_kardex_reference (reference_type, reference_id)}; {@code branch_id} narrows
	 * to the origin branch the caller already knows, so two transfers cannot collide on the same
	 * reference id across branches.
	 */
	@Query(value = """
			SELECT product_id AS productId, unit_cost AS unitCost FROM kardex_movements
			WHERE branch_id = :branchId AND reference_type = :referenceType AND reference_id = :referenceId
			""", nativeQuery = true)
	List<ProductUnitCostRow> findOutboundUnitCosts(@Param("branchId") Long branchId,
			@Param("referenceType") String referenceType, @Param("referenceId") String referenceId);

	interface ProductUnitCostRow {

		Long getProductId();

		java.math.BigDecimal getUnitCost();
	}
}
