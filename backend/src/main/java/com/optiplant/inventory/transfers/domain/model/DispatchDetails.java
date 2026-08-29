package com.optiplant.inventory.transfers.domain.model;

import java.time.Instant;
import java.util.UUID;

/**
 * The dispatch header fields R-10 requires recorded alongside the per-item lines: carrier,
 * tracking number, {@code dispatched_at}, {@code dispatched_by_user_id} and
 * {@code estimated_arrival_at} (P-11 — precomputed by the application service via
 * {@code RouteLeadTimePort}, or the operator's own value when no active route answers).
 */
public record DispatchDetails(CarrierName carrierName, String trackingNumber, Instant dispatchedAt,
		Instant estimatedArrivalAt, UUID dispatchedByUserExternalId) {
}
