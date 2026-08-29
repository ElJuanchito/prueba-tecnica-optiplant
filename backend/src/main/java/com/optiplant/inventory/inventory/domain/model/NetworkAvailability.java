package com.optiplant.inventory.inventory.domain.model;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * A product's balances across every active branch (CU-INV-04, contract §6). {@code networkTotal}
 * is the sum of {@code currentStock} across {@code branches}; an explicit empty result — never a
 * {@code 404} — when the network total is zero (R-05).
 */
public record NetworkAvailability(UUID productExternalId, String sku, String name,
		List<BranchAvailability> branches, BigDecimal networkTotal) {
}
