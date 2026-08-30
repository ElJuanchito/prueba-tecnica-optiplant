package com.optiplant.inventory.sales.infrastructure.adapter.out.persistence;

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
 * Spring Data JPA repository for {@link SaleJpaEntity} (design §6.1, §6.3, §7).
 *
 * <p>{@link #findByExternalId} uses {@code @Lock(LockModeType.PESSIMISTIC_WRITE)} with NO
 * {@code @QueryHints} timeout (design §6.1).
 */
public interface SaleSpringDataRepository extends JpaRepository<SaleJpaEntity, Long> {

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	Optional<SaleJpaEntity> findByExternalId(UUID externalId);

	Optional<SaleJpaEntity> findDetailByExternalId(UUID externalId);

	Optional<SaleJpaEntity> findByInvoiceNumber(String invoiceNumber);

	@Query(value = "SELECT pg_advisory_xact_lock(hashtext(:key)) IS NULL", nativeQuery = true)
	boolean allocateAdvisoryLock(@Param("key") String key);

	@Query(value = """
			SELECT COALESCE(MAX(CAST(SUBSTRING(invoice_number FROM 10) AS INTEGER)), 0) + 1
			FROM sales WHERE invoice_number LIKE :pattern
			""", nativeQuery = true)
	int nextSequenceNumber(@Param("pattern") String pattern);

	@Query(value = """
			SELECT * FROM sales
			WHERE (:branchId IS NULL OR branch_id = :branchId)
			  AND (:customerId IS NULL OR customer_id = :customerId)
			  AND (:status IS NULL OR status = :status)
			  AND (CAST(:from AS timestamptz) IS NULL OR created_at >= CAST(:from AS timestamptz))
			  AND (CAST(:to AS timestamptz) IS NULL OR created_at <= CAST(:to AS timestamptz))
			ORDER BY created_at DESC
			""", countQuery = """
			SELECT count(*) FROM sales
			WHERE (:branchId IS NULL OR branch_id = :branchId)
			  AND (:customerId IS NULL OR customer_id = :customerId)
			  AND (:status IS NULL OR status = :status)
			  AND (CAST(:from AS timestamptz) IS NULL OR created_at >= CAST(:from AS timestamptz))
			  AND (CAST(:to AS timestamptz) IS NULL OR created_at <= CAST(:to AS timestamptz))
			""", nativeQuery = true)
	Page<SaleJpaEntity> searchOrderByCreatedAt(@Param("branchId") Long branchId,
			@Param("customerId") Long customerId,
			@Param("status") String status, @Param("from") Instant from, @Param("to") Instant to, Pageable pageable);

	@Query(value = """
			SELECT * FROM sales
			WHERE (:branchId IS NULL OR branch_id = :branchId)
			  AND (:customerId IS NULL OR customer_id = :customerId)
			  AND (:status IS NULL OR status = :status)
			  AND (CAST(:from AS timestamptz) IS NULL OR created_at >= CAST(:from AS timestamptz))
			  AND (CAST(:to AS timestamptz) IS NULL OR created_at <= CAST(:to AS timestamptz))
			ORDER BY total_amount DESC, created_at DESC
			""", countQuery = """
			SELECT count(*) FROM sales
			WHERE (:branchId IS NULL OR branch_id = :branchId)
			  AND (:customerId IS NULL OR customer_id = :customerId)
			  AND (:status IS NULL OR status = :status)
			  AND (CAST(:from AS timestamptz) IS NULL OR created_at >= CAST(:from AS timestamptz))
			  AND (CAST(:to AS timestamptz) IS NULL OR created_at <= CAST(:to AS timestamptz))
			""", nativeQuery = true)
	Page<SaleJpaEntity> searchOrderByTotalAmount(@Param("branchId") Long branchId,
			@Param("customerId") Long customerId,
			@Param("status") String status, @Param("from") Instant from, @Param("to") Instant to, Pageable pageable);

	@Query(value = """
			SELECT COUNT(*) AS salesCount, COALESCE(SUM(total_amount), 0) AS totalAmount
			FROM sales
			WHERE (:branchId IS NULL OR branch_id = :branchId)
			  AND (:customerId IS NULL OR customer_id = :customerId)
			  AND (:status IS NULL OR status = :status)
			  AND (CAST(:from AS timestamptz) IS NULL OR created_at >= CAST(:from AS timestamptz))
			  AND (CAST(:to AS timestamptz) IS NULL OR created_at <= CAST(:to AS timestamptz))
			""", nativeQuery = true)
	SaleAggregatesRow computeAggregates(@Param("branchId") Long branchId,
			@Param("customerId") Long customerId,
			@Param("status") String status, @Param("from") Instant from, @Param("to") Instant to);

	interface SaleAggregatesRow {
		Long getSalesCount();

		BigDecimal getTotalAmount();
	}
}
