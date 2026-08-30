package com.optiplant.inventory.logistics.domain.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * Domain representation of one {@code logistics_routes} row (design §4). Immutable — {@link
 * #update} and {@link #deactivate} return a new instance. Routes are directional: A&rarr;B and
 * B&rarr;A are two independent rows (F-6); deactivation is logical, never a delete (R-24).
 */
public record LogisticsRoute(UUID externalId, UUID originBranchExternalId, UUID destinationBranchExternalId,
		RouteDuration estimatedDurationHours, TransportCost transportCost, RoutePriority priorityLevel,
		boolean active, Instant createdAt) {

	public LogisticsRoute update(RouteDuration duration, TransportCost cost, RoutePriority priority) {
		return new LogisticsRoute(externalId, originBranchExternalId, destinationBranchExternalId, duration, cost,
				priority, active, createdAt);
	}

	/** Logical deactivation only (R-24, F-6) — MUST NOT break transfers already dispatched under this route. */
	public LogisticsRoute deactivate() {
		return new LogisticsRoute(externalId, originBranchExternalId, destinationBranchExternalId,
				estimatedDurationHours, transportCost, priorityLevel, false, createdAt);
	}

	/** {@code estimatedDurationHours} rendered as a {@link Duration}, for {@code RouteLeadTimePort} (P-11). */
	public Duration leadTime() {
		BigDecimal seconds = estimatedDurationHours.value().multiply(BigDecimal.valueOf(3600));
		return Duration.ofSeconds(seconds.setScale(0, RoundingMode.HALF_UP).longValueExact());
	}
}
