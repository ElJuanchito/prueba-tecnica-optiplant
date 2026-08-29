package com.optiplant.inventory.logistics.application.port.out;

import com.optiplant.inventory.logistics.domain.model.ActiveTransferPage;
import com.optiplant.inventory.logistics.domain.model.DelayedTransfer;
import com.optiplant.inventory.logistics.domain.model.DeliveryOutcome;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * How {@code logistics} reads {@code transfers} rows (P-12, design §6.3): read-only native
 * projections, never a JPA {@code @Entity} over {@code transfers}/{@code transfer_items} and
 * never a {@code save}/{@code delete} method — {@code transfers} is the only writer of its own
 * tables, enforced structurally, not by review promise alone.
 */
public interface TransferMonitorReadPort {

	/** R-25 — active transfers ({@code REQUESTED}, {@code IN_PREPARATION}, {@code IN_TRANSIT}) involving the caller's branch on either side. */
	ActiveTransferPage listActive(ActiveTransferFilter filter);

	/** R-26 — delivered transfers ({@code actual_arrival_at} in range) for the compliance report to fold. */
	List<DeliveryOutcome> listDeliveries(DeliveryFilter filter);

	/** R-28 — {@code IN_TRANSIT} transfers whose {@code estimated_arrival_at} is before {@code now}. */
	List<DelayedTransfer> listDelayed(Instant now);

	/** @param callerBranchExternalId {@code null} for a network-wide {@code ADMIN} (RN-08) */
	record ActiveTransferFilter(UUID callerBranchExternalId, String status, Boolean delayedOnly, int page, int size) {
	}

	/** @param callerBranchExternalId {@code null} for a network-wide {@code ADMIN} (RN-08) */
	record DeliveryFilter(UUID callerBranchExternalId, Instant from, Instant to) {
	}
}
