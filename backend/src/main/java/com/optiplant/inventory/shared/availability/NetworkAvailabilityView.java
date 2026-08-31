package com.optiplant.inventory.shared.availability;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Consolidated balance across every active branch for one product (CU-EXT-01, contract §6).
 *
 * <p>Deliberately carries no {@code isOwnBranch} marker (R-24, D-1) — an external system has no
 * branch.
 */
public record NetworkAvailabilityView(UUID productExternalId, String sku, String name,
		List<BranchAvailabilityView> branches, BigDecimal networkTotal) {
}
