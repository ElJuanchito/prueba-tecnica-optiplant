package com.optiplant.inventory.inventory.domain.model;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * One branch's balance in the network-availability response (CU-INV-04, contract §6, R-04).
 *
 * @param isOwnBranch {@code true}/{@code false} for a {@code BRANCH_MANAGER}/{@code OPERATOR}
 *                    caller — exactly one branch is {@code true}; {@code null} for a corporate
 *                    {@code ADMIN}, whose marker MUST be absent, not fabricated (R-04)
 */
public record BranchAvailability(UUID branchExternalId, String branchName, BigDecimal currentStock,
		BigDecimal reservedStock, BigDecimal inTransitStock, BigDecimal availableStock, Boolean isOwnBranch) {
}
