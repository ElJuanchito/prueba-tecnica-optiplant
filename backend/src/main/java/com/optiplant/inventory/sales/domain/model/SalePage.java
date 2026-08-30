package com.optiplant.inventory.sales.domain.model;

import java.util.List;

/**
 * Paginated sale listing result including aggregates (R-24, contract §6).
 */
public record SalePage(
		List<SaleSummary> content,
		long totalElements,
		int page,
		int size,
		SaleAggregates aggregates
) {
}
