package com.optiplant.inventory.shared.availability;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * One active branch's stock balances in {@link NetworkAvailabilityView} (CU-EXT-01, contract §6).
 */
public record BranchAvailabilityView(UUID branchExternalId, String branchName, BigDecimal currentStock,
		BigDecimal reservedStock, BigDecimal inTransitStock, BigDecimal availableStock) {
}
