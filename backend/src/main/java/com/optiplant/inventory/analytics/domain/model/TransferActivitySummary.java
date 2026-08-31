package com.optiplant.inventory.analytics.domain.model;

/**
 * Active-transfer summary split by inbound/outbound plus delayed count (CU-DSH-01, RF-DSH-03, R-12).
 */
public record TransferActivitySummary(TransferStatusCounts inbound, TransferStatusCounts outbound,
		long delayedCount) {
}
