package com.optiplant.inventory.transfers.domain.model;

import java.time.Instant;
import java.util.UUID;

/** One row of a transfer listing (contract §6 {@code GET /api/transfers}). */
public record TransferSummary(UUID externalId, TransferNumber number, TransferStatus status,
		TransferPriority priority, BranchReference originBranch, BranchReference destinationBranch,
		Instant createdAt, Instant estimatedArrivalAt) {
}
