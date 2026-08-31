package com.optiplant.inventory.analytics.domain.model;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * One active branch's aggregated indicators for the corporate comparative board (CU-DSH-03, RF-DSH-05, R-20).
 */
public record BranchPerformance(UUID branchExternalId, String code, String name, BigDecimal salesAmount,
		long salesCount, BigDecimal unitsSold, BigDecimal inventoryValue, long criticalProductCount,
		long activeTransferCount) {
}
