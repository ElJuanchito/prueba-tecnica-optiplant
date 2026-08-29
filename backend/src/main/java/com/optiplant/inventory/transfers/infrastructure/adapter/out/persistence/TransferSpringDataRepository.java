package com.optiplant.inventory.transfers.infrastructure.adapter.out.persistence;

import jakarta.persistence.LockModeType;
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
 * Spring Data repository for {@code transfers} (design §6.1, §6.2, §7).
 *
 * <p>{@link #findByExternalId} is the {@code SELECT ... FOR UPDATE} derived query T-02 requires
 * before any transition (F-5) — {@code @Lock(PESSIMISTIC_WRITE)}, no {@code @QueryHints} lock
 * timeout: verified against {@code hibernate-core-7.4.5.Final}, {@code PostgreSQLDialect} renders
 * only {@code for update}/{@code for update nowait}/{@code for update skip locked} (design §6.1
 * verbatim, {@code inventory}'s {@code BranchInventorySpringDataRepository}). Any lock failure
 * surfaces as {@code PessimisticLockingFailureException}, mapped to
 * {@code 409 concurrent_transfer_update}. {@link #findDetailByExternalId} is the no-lock
 * counterpart for reads (T-05).
 *
 * <p>{@link #allocateAdvisoryLock} and {@link #nextSequenceNumber} back D-3's year-scoped
 * transfer-number allocation (§6.2, DT-11) — same technique as {@code notifications}'
 * {@code AlertSpringDataRepository#advisoryLock} (DT-09).
 */
public interface TransferSpringDataRepository extends JpaRepository<TransferJpaEntity, Long> {

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	Optional<TransferJpaEntity> findByExternalId(UUID externalId);

	/** No lock (T-05, RN-09) — used by {@code findByExternalId} reads and to re-fetch after {@code save}. */
	Optional<TransferJpaEntity> findDetailByExternalId(UUID externalId);

	@Query(value = "SELECT pg_advisory_xact_lock(hashtext(:key)) IS NULL", nativeQuery = true)
	boolean allocateAdvisoryLock(@Param("key") String key);

	/**
	 * MUST run only after {@link #allocateAdvisoryLock} in the same transaction (§6.2). The
	 * suffix widens past {@code 9999} rather than truncating.
	 */
	@Query(value = """
			SELECT COALESCE(MAX(CAST(SUBSTRING(transfer_number FROM 10) AS INTEGER)), 0) + 1
			FROM transfers WHERE transfer_number LIKE :pattern
			""", nativeQuery = true)
	int nextSequenceNumber(@Param("pattern") String pattern);

	/** Sorted by {@code created_at} descending — the default and only ordering for {@code sort=createdAt}. */
	@Query(value = """
			SELECT * FROM transfers t
			WHERE (:originId IS NULL OR t.origin_branch_id = :originId)
			  AND (:destinationId IS NULL OR t.destination_branch_id = :destinationId)
			  AND (:eitherId IS NULL OR t.origin_branch_id = :eitherId OR t.destination_branch_id = :eitherId)
			  AND (:status IS NULL OR t.status = :status)
			  AND (CAST(:from AS timestamptz) IS NULL OR t.created_at >= CAST(:from AS timestamptz))
			  AND (CAST(:to AS timestamptz) IS NULL OR t.created_at <= CAST(:to AS timestamptz))
			ORDER BY t.created_at DESC
			""", countQuery = """
			SELECT count(*) FROM transfers t
			WHERE (:originId IS NULL OR t.origin_branch_id = :originId)
			  AND (:destinationId IS NULL OR t.destination_branch_id = :destinationId)
			  AND (:eitherId IS NULL OR t.origin_branch_id = :eitherId OR t.destination_branch_id = :eitherId)
			  AND (:status IS NULL OR t.status = :status)
			  AND (CAST(:from AS timestamptz) IS NULL OR t.created_at >= CAST(:from AS timestamptz))
			  AND (CAST(:to AS timestamptz) IS NULL OR t.created_at <= CAST(:to AS timestamptz))
			""", nativeQuery = true)
	Page<TransferJpaEntity> searchOrderByCreatedAt(@Param("originId") Long originId,
			@Param("destinationId") Long destinationId, @Param("eitherId") Long eitherId,
			@Param("status") String status, @Param("from") Instant from, @Param("to") Instant to, Pageable pageable);

	/**
	 * {@code sort=priority} (contract §6) — F-1's token has no column, so the ordering is computed
	 * from the deterministic first line of {@code notes}: {@code URGENT}, then {@code STANDARD} or
	 * absent (matching {@code TransferNotes.parse}'s default), then {@code LOW}; recency breaks
	 * ties.
	 */
	@Query(value = """
			SELECT * FROM transfers t
			WHERE (:originId IS NULL OR t.origin_branch_id = :originId)
			  AND (:destinationId IS NULL OR t.destination_branch_id = :destinationId)
			  AND (:eitherId IS NULL OR t.origin_branch_id = :eitherId OR t.destination_branch_id = :eitherId)
			  AND (:status IS NULL OR t.status = :status)
			  AND (CAST(:from AS timestamptz) IS NULL OR t.created_at >= CAST(:from AS timestamptz))
			  AND (CAST(:to AS timestamptz) IS NULL OR t.created_at <= CAST(:to AS timestamptz))
			ORDER BY CASE
			             WHEN t.notes LIKE 'PRIORITY:URGENT%' THEN 0
			             WHEN t.notes LIKE 'PRIORITY:LOW%' THEN 2
			             ELSE 1
			         END ASC, t.created_at DESC
			""", countQuery = """
			SELECT count(*) FROM transfers t
			WHERE (:originId IS NULL OR t.origin_branch_id = :originId)
			  AND (:destinationId IS NULL OR t.destination_branch_id = :destinationId)
			  AND (:eitherId IS NULL OR t.origin_branch_id = :eitherId OR t.destination_branch_id = :eitherId)
			  AND (:status IS NULL OR t.status = :status)
			  AND (CAST(:from AS timestamptz) IS NULL OR t.created_at >= CAST(:from AS timestamptz))
			  AND (CAST(:to AS timestamptz) IS NULL OR t.created_at <= CAST(:to AS timestamptz))
			""", nativeQuery = true)
	Page<TransferJpaEntity> searchOrderByPriority(@Param("originId") Long originId,
			@Param("destinationId") Long destinationId, @Param("eitherId") Long eitherId,
			@Param("status") String status, @Param("from") Instant from, @Param("to") Instant to, Pageable pageable);
}
