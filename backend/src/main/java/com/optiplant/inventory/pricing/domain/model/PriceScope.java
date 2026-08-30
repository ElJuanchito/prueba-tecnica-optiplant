package com.optiplant.inventory.pricing.domain.model;

/**
 * The applicability scope of a price row (contract §5, design §3).
 */
public enum PriceScope {
	/** Applies corporate-wide across all branches where no branch exception exists. */
	CORPORATE,
	/** Branch-specific exception price. */
	BRANCH
}
