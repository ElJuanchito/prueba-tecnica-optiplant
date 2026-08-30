package com.optiplant.inventory.sales.domain.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Detailed representation of a sale (contract §6).
 *
 * <p>{@code notes} contains only the human note portion (with the F-3 token stripped).
 * {@code cancellationReason} exposes the parsed cancellation reason string, if cancelled.
 */
public record SaleDetail(
		UUID externalId,
		String invoiceNumber,
		SaleStatus status,
		BranchRef branch,
		UserRef soldBy,
		PriceListRef priceList,
		String customerName,
		String customerTaxId,
		BigDecimal subtotal,
		BigDecimal discountAmount,
		BigDecimal taxAmount,
		BigDecimal totalAmount,
		String notes,
		String cancellationReason,
		Instant createdAt,
		List<SaleItemView> items
) {
}
