package com.optiplant.inventory.analytics.domain.model;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * One product's rotation and ABC indicators for a period (CU-DSH-01, RF-DSH-02, R-08).
 */
public record RotationLine(UUID productExternalId, String sku, String name, BigDecimal unitsSold,
		BigDecimal salesAmount, BigDecimal sharePercent, BigDecimal cumulativeSharePercent,
		AbcClass abcClass, BigDecimal coverageDays) {
}
