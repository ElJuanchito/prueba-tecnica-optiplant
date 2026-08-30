package com.optiplant.inventory.purchases.domain.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Summary view of a purchase order for paginated listings (contract §6, R-24): its number,
 * supplier, status, {@code total_amount} and reception date.
 */
public record PurchaseOrderSummary(UUID externalId, String orderNumber, PurchaseOrderStatus status, BranchRef branch,
		SupplierRef supplier, BigDecimal totalAmount, Instant createdAt, Instant receivedAt) {
}
