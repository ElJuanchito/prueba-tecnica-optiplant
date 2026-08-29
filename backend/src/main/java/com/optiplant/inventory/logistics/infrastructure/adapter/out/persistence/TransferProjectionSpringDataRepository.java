package com.optiplant.inventory.logistics.infrastructure.adapter.out.persistence;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/**
 * How {@code logistics} reads {@code transfers} rows (P-12, design §6.3): the bare
 * {@link Repository} marker interface, which declares <strong>no</strong> {@code save},
 * {@code delete} or {@code flush} method, and <strong>no {@code @Modifying}</strong> query.
 * Bound to {@link LogisticsRouteJpaEntity} — the only {@code @Entity} this module owns — purely
 * because Spring Data JPA's repository factory needs a registered entity type; no {@code @Entity}
 * for {@code transfers}/{@code transfer_items} exists anywhere in this module, so read-only
 * enforcement is structural, not a review promise (ArchUnit cannot see SQL, P-12).
 *
 * <p>Three native {@code SELECT}s only: the R-25 monitor list, the R-26 delivery outcomes for
 * compliance, and the R-28 delayed scan. Branch names are joined directly from {@code branches}
 * — a native read across another module's table, never a write (P-12 forbids only mutation).
 */
public interface TransferProjectionSpringDataRepository extends Repository<LogisticsRouteJpaEntity, Long> {

	/**
	 * R-25 — active transfers ({@code REQUESTED}, {@code IN_PREPARATION}, {@code IN_TRANSIT})
	 * involving the caller's branch on either side, item count and requested-quantity sum via a
	 * {@code LEFT JOIN} aggregate so a transfer with no items still appears (design §6.3).
	 */
	@Query(value = """
			SELECT t.external_id AS transferExternalId, t.transfer_number AS transferNumber, t.status AS status,
			       ob.external_id AS originBranchExternalId, ob.name AS originBranchName,
			       db.external_id AS destinationBranchExternalId, db.name AS destinationBranchName,
			       t.notes AS notes, t.estimated_arrival_at AS estimatedArrivalAt,
			       COALESCE(agg.item_count, 0) AS itemCount, COALESCE(agg.total_quantity, 0) AS totalQuantity
			FROM transfers t
			JOIN branches ob ON ob.id = t.origin_branch_id
			JOIN branches db ON db.id = t.destination_branch_id
			LEFT JOIN (
			    SELECT transfer_id, COUNT(*) AS item_count, SUM(requested_quantity) AS total_quantity
			    FROM transfer_items GROUP BY transfer_id
			) agg ON agg.transfer_id = t.id
			WHERE t.status IN ('REQUESTED', 'IN_PREPARATION', 'IN_TRANSIT')
			  AND (:callerBranchId IS NULL OR t.origin_branch_id = :callerBranchId OR t.destination_branch_id = :callerBranchId)
			  AND (:status IS NULL OR t.status = :status)
			  AND (:delayedOnly = FALSE
			       OR (t.status = 'IN_TRANSIT' AND t.estimated_arrival_at IS NOT NULL AND t.estimated_arrival_at < now()))
			ORDER BY t.created_at DESC
			""", countQuery = """
			SELECT count(*) FROM transfers t
			WHERE t.status IN ('REQUESTED', 'IN_PREPARATION', 'IN_TRANSIT')
			  AND (:callerBranchId IS NULL OR t.origin_branch_id = :callerBranchId OR t.destination_branch_id = :callerBranchId)
			  AND (:status IS NULL OR t.status = :status)
			  AND (:delayedOnly = FALSE
			       OR (t.status = 'IN_TRANSIT' AND t.estimated_arrival_at IS NOT NULL AND t.estimated_arrival_at < now()))
			""", nativeQuery = true)
	Page<ActiveTransferRow> searchActive(@Param("callerBranchId") Long callerBranchId, @Param("status") String status,
			@Param("delayedOnly") boolean delayedOnly, Pageable pageable);

	/** R-26 — delivered transfers ({@code actual_arrival_at} in range) for the compliance report to fold. */
	@Query(value = """
			SELECT ob.external_id AS originBranchExternalId, db.external_id AS destinationBranchExternalId,
			       t.estimated_arrival_at AS estimatedArrivalAt, t.actual_arrival_at AS actualArrivalAt
			FROM transfers t
			JOIN branches ob ON ob.id = t.origin_branch_id
			JOIN branches db ON db.id = t.destination_branch_id
			WHERE t.status IN ('RECEIVED', 'RECEIVED_WITH_DISCREPANCY')
			  AND t.actual_arrival_at IS NOT NULL
			  AND (:callerBranchId IS NULL OR t.origin_branch_id = :callerBranchId OR t.destination_branch_id = :callerBranchId)
			  AND (CAST(:from AS timestamptz) IS NULL OR t.actual_arrival_at >= CAST(:from AS timestamptz))
			  AND (CAST(:to AS timestamptz) IS NULL OR t.actual_arrival_at <= CAST(:to AS timestamptz))
			""", nativeQuery = true)
	List<DeliveryOutcomeRow> searchDeliveries(@Param("callerBranchId") Long callerBranchId, @Param("from") Instant from,
			@Param("to") Instant to);

	/** R-28 — {@code IN_TRANSIT} transfers whose {@code estimated_arrival_at} is before {@code now}. */
	@Query(value = """
			SELECT t.external_id AS transferExternalId, t.transfer_number AS transferNumber,
			       ob.external_id AS originBranchExternalId, db.external_id AS destinationBranchExternalId
			FROM transfers t
			JOIN branches ob ON ob.id = t.origin_branch_id
			JOIN branches db ON db.id = t.destination_branch_id
			WHERE t.status = 'IN_TRANSIT' AND t.estimated_arrival_at IS NOT NULL AND t.estimated_arrival_at < :now
			""", nativeQuery = true)
	List<DelayedTransferRow> searchDelayed(@Param("now") Instant now);

	interface ActiveTransferRow {

		UUID getTransferExternalId();

		String getTransferNumber();

		String getStatus();

		UUID getOriginBranchExternalId();

		String getOriginBranchName();

		UUID getDestinationBranchExternalId();

		String getDestinationBranchName();

		String getNotes();

		Instant getEstimatedArrivalAt();

		long getItemCount();

		BigDecimal getTotalQuantity();
	}

	interface DeliveryOutcomeRow {

		UUID getOriginBranchExternalId();

		UUID getDestinationBranchExternalId();

		Instant getEstimatedArrivalAt();

		Instant getActualArrivalAt();
	}

	interface DelayedTransferRow {

		UUID getTransferExternalId();

		String getTransferNumber();

		UUID getOriginBranchExternalId();

		UUID getDestinationBranchExternalId();
	}
}
