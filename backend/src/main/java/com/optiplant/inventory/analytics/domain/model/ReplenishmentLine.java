package com.optiplant.inventory.analytics.domain.model;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * One item in the critical replenishment panel (CU-DSH-02, RF-DSH-04, R-15).
 */
public record ReplenishmentLine(UUID productExternalId, String sku, String name, BigDecimal currentStock,
		BigDecimal minStockThreshold, ReplenishmentSeverity severity, BigDecimal coverageDays) {
}
