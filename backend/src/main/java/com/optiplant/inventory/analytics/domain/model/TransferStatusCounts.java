package com.optiplant.inventory.analytics.domain.model;

/**
 * Active transfer counts grouped by status (CU-DSH-01, RF-DSH-03, R-12).
 */
public record TransferStatusCounts(long requested, long inPreparation, long inTransit) {
}
