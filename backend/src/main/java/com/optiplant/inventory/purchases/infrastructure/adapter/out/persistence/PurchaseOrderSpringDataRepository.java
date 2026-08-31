package com.optiplant.inventory.purchases.infrastructure.adapter.out.persistence;

import jakarta.persistence.LockModeType;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Spring Data JPA repository for {@link PurchaseOrderJpaEntity} (design §6.1, §6.2, §6.3, §7).
 *
 * <p>{@link #findByExternalId} uses {@code @Lock(LockModeType.PESSIMISTIC_WRITE)} with NO
 * {@code @QueryHints} timeout (design §6.1, F-5).
 */
public interface PurchaseOrderSpringDataRepository extends JpaRepository<PurchaseOrderJpaEntity, Long> {

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	Optional<PurchaseOrderJpaEntity> findByExternalId(UUID externalId);

	Optional<PurchaseOrderJpaEntity> findDetailByExternalId(UUID externalId);

	@Query(value = "SELECT pg_advisory_xact_lock(hashtext(:key)) IS NULL", nativeQuery = true)
	boolean allocateAdvisoryLock(@Param("key") String key);

	@Query(value = """
			SELECT COALESCE(MAX(CAST(SUBSTRING(order_number FROM 9) AS INTEGER)), 0) + 1
			FROM purchase_orders WHERE order_number LIKE :pattern
			""", nativeQuery = true)
	int nextSequenceNumber(@Param("pattern") String pattern);

	@Query(value = """
			SELECT * FROM purchase_orders po
			WHERE (:branchId IS NULL OR po.branch_id = :branchId)
			  AND (:supplierId IS NULL OR po.supplier_id = :supplierId)
			  AND (:productId IS NULL OR EXISTS (SELECT 1 FROM purchase_order_items poi WHERE poi.purchase_order_id = po.id AND poi.product_id = :productId))
			  AND (:status IS NULL OR po.status = :status)
			  AND (CAST(:from AS timestamptz) IS NULL OR po.created_at >= CAST(:from AS timestamptz))
			  AND (CAST(:to AS timestamptz) IS NULL OR po.created_at <= CAST(:to AS timestamptz))
			ORDER BY po.created_at DESC
			""", countQuery = """
			SELECT COUNT(*) FROM purchase_orders po
			WHERE (:branchId IS NULL OR po.branch_id = :branchId)
			  AND (:supplierId IS NULL OR po.supplier_id = :supplierId)
			  AND (:productId IS NULL OR EXISTS (SELECT 1 FROM purchase_order_items poi WHERE poi.purchase_order_id = po.id AND poi.product_id = :productId))
			  AND (:status IS NULL OR po.status = :status)
			  AND (CAST(:from AS timestamptz) IS NULL OR po.created_at >= CAST(:from AS timestamptz))
			  AND (CAST(:to AS timestamptz) IS NULL OR po.created_at <= CAST(:to AS timestamptz))
			""", nativeQuery = true)
	Page<PurchaseOrderJpaEntity> searchOrderByCreatedAt(
			@Param("branchId") Long branchId,
			@Param("supplierId") Long supplierId,
			@Param("productId") Long productId,
			@Param("status") String status,
			@Param("from") Instant from,
			@Param("to") Instant to,
			Pageable pageable);

	@Query(value = """
			SELECT * FROM purchase_orders po
			WHERE (:branchId IS NULL OR po.branch_id = :branchId)
			  AND (:supplierId IS NULL OR po.supplier_id = :supplierId)
			  AND (:productId IS NULL OR EXISTS (SELECT 1 FROM purchase_order_items poi WHERE poi.purchase_order_id = po.id AND poi.product_id = :productId))
			  AND (:status IS NULL OR po.status = :status)
			  AND (CAST(:from AS timestamptz) IS NULL OR po.created_at >= CAST(:from AS timestamptz))
			  AND (CAST(:to AS timestamptz) IS NULL OR po.created_at <= CAST(:to AS timestamptz))
			ORDER BY po.total_amount DESC, po.created_at DESC
			""", countQuery = """
			SELECT COUNT(*) FROM purchase_orders po
			WHERE (:branchId IS NULL OR po.branch_id = :branchId)
			  AND (:supplierId IS NULL OR po.supplier_id = :supplierId)
			  AND (:productId IS NULL OR EXISTS (SELECT 1 FROM purchase_order_items poi WHERE poi.purchase_order_id = po.id AND poi.product_id = :productId))
			  AND (:status IS NULL OR po.status = :status)
			  AND (CAST(:from AS timestamptz) IS NULL OR po.created_at >= CAST(:from AS timestamptz))
			  AND (CAST(:to AS timestamptz) IS NULL OR po.created_at <= CAST(:to AS timestamptz))
			""", nativeQuery = true)
	Page<PurchaseOrderJpaEntity> searchOrderByTotalAmount(
			@Param("branchId") Long branchId,
			@Param("supplierId") Long supplierId,
			@Param("productId") Long productId,
			@Param("status") String status,
			@Param("from") Instant from,
			@Param("to") Instant to,
			Pageable pageable);

	@Query(value = """
			SELECT po.external_id AS orderExternalId,
			       po.order_number AS orderNumber,
			       s.external_id AS supplierExternalId,
			       s.tax_id AS supplierTaxId,
			       s.name AS supplierName,
			       poi.unit_cost AS unitCost,
			       poi.discount_percent AS discountPercent,
			       poi.ordered_quantity AS quantity,
			       po.created_at AS orderedAt,
			       po.received_at AS receivedAt
			FROM purchase_orders po
			JOIN purchase_order_items poi ON poi.purchase_order_id = po.id
			JOIN suppliers s ON s.id = po.supplier_id
			WHERE poi.product_id = :productId
			  AND (:supplierId IS NULL OR po.supplier_id = :supplierId)
			  AND (:branchId IS NULL OR po.branch_id = :branchId)
			  AND (CAST(:from AS timestamptz) IS NULL OR po.created_at >= CAST(:from AS timestamptz))
			  AND (CAST(:to AS timestamptz) IS NULL OR po.created_at <= CAST(:to AS timestamptz))
			ORDER BY po.created_at DESC
			""", countQuery = """
			SELECT COUNT(*)
			FROM purchase_orders po
			JOIN purchase_order_items poi ON poi.purchase_order_id = po.id
			JOIN suppliers s ON s.id = po.supplier_id
			WHERE poi.product_id = :productId
			  AND (:supplierId IS NULL OR po.supplier_id = :supplierId)
			  AND (:branchId IS NULL OR po.branch_id = :branchId)
			  AND (CAST(:from AS timestamptz) IS NULL OR po.created_at >= CAST(:from AS timestamptz))
			  AND (CAST(:to AS timestamptz) IS NULL OR po.created_at <= CAST(:to AS timestamptz))
			""", nativeQuery = true)
	Page<CostHistoryRow> findCostHistory(
			@Param("productId") Long productId,
			@Param("supplierId") Long supplierId,
			@Param("branchId") Long branchId,
			@Param("from") Instant from,
			@Param("to") Instant to,
			Pageable pageable);

	interface CostHistoryRow {
		UUID getOrderExternalId();

		String getOrderNumber();

		UUID getSupplierExternalId();

		String getSupplierTaxId();

		String getSupplierName();

		BigDecimal getUnitCost();

		BigDecimal getDiscountPercent();

		BigDecimal getQuantity();

		Instant getOrderedAt();

		Instant getReceivedAt();
	}
}
