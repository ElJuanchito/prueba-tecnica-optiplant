package com.optiplant.inventory.sales.domain.model;

import java.math.BigDecimal;

/**
 * Aggregated statistics for filtered sales listings (R-24, contract §6).
 */
public record SaleAggregates(long salesCount, BigDecimal totalAmount) {

	public static SaleAggregates empty() {
		return new SaleAggregates(0L, BigDecimal.ZERO);
	}
}
