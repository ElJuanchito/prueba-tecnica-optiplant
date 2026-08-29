package com.optiplant.inventory.logistics.domain.model;

import java.time.Instant;
import java.util.UUID;

/**
 * One delivered transfer's timing, read through {@code TransferMonitorReadPort} (P-12, §6.3) and
 * folded by {@code DeliveryComplianceCalculator} into a {@link ComplianceRow} (R-26).
 * {@code estimatedArrivalAt} is {@code null} for a transfer whose dispatch precomputed no ETA
 * (P-11) — excluded from the percentage, counted as unmeasured, never scored as late.
 */
public record DeliveryOutcome(UUID originBranchExternalId, UUID destinationBranchExternalId,
		Instant estimatedArrivalAt, Instant actualArrivalAt) {
}
