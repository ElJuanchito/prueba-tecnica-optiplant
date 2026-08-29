package com.optiplant.inventory.logistics.domain.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** A route enriched with branch names for the API surface (contract §6). */
public record RouteSummary(UUID externalId, BranchReference originBranch, BranchReference destinationBranch,
		BigDecimal estimatedDurationHours, BigDecimal transportCost, RoutePriority priorityLevel, boolean active,
		Instant createdAt) {
}
