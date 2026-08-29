package com.optiplant.inventory.logistics.domain.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * One row of the active-transfer monitor (CU-LOG-02, R-25, contract §6). {@code status} stays a
 * plain {@code String} — {@code logistics} reads {@code transfers.status} through a native
 * projection (P-12, §6.3) and declares no dependency on {@code transfers}' own
 * {@code TransferStatus} enum.
 */
public record ActiveTransferView(UUID transferExternalId, String transferNumber, String status,
		BranchReference originBranch, BranchReference destinationBranch, String priority, long itemCount,
		BigDecimal totalQuantity, Instant estimatedArrivalAt, boolean isDelayed) {
}
