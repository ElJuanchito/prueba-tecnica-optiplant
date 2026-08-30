package com.optiplant.inventory.sales.domain.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Summary view of a sale for paginated listings (contract §6, R-24).
 */
public record SaleSummary(
		UUID externalId,
		String invoiceNumber,
		SaleStatus status,
		BranchRef branch,
		UserRef soldBy,
		PriceListRef priceList,
		CustomerRef customer,
		String customerName,
		BigDecimal totalAmount,
		Instant createdAt
) {
}
