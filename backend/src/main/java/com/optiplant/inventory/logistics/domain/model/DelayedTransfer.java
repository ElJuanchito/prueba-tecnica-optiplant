package com.optiplant.inventory.logistics.domain.model;

import java.util.UUID;

/**
 * One {@code IN_TRANSIT} transfer past its {@code estimated_arrival_at}, read by the scheduled
 * detector (R-28) through {@code TransferMonitorReadPort#listDelayed}.
 */
public record DelayedTransfer(UUID transferExternalId, String transferNumber, UUID originBranchExternalId,
		UUID destinationBranchExternalId) {
}
