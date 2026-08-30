package com.optiplant.inventory.transfers.domain.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** The full transfer view (contract §6); {@code observations} is {@code notes} with the F-1 token stripped. */
public record TransferDetail(UUID externalId, TransferNumber number, TransferStatus status,
		TransferPriority priority, BranchReference originBranch, BranchReference destinationBranch,
		CarrierName carrierName, String trackingNumber, Instant dispatchedAt, Instant estimatedArrivalAt,
		Instant actualArrivalAt, BigDecimal deviationHours, List<String> observations,
		UUID requestedByUserExternalId, UUID dispatchedByUserExternalId, UUID receivedByUserExternalId,
		Instant createdAt, Instant updatedAt, List<TransferItemView> items) {
}
