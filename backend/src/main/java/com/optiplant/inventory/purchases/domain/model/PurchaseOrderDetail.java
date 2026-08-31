package com.optiplant.inventory.purchases.domain.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Full read representation of a purchase order (contract §6, R-27). {@code notes} is the human
 * portion with the F-3 token stripped; {@code cancellationReason} is the parsed reason, if any.
 * No numeric {@code id} appears anywhere.
 */
public record PurchaseOrderDetail(UUID externalId, String orderNumber, PurchaseOrderStatus status, BranchRef branch,
		SupplierRef supplier, UserRef createdBy, String paymentTerms, BigDecimal totalAmount, String notes,
		String cancellationReason, Instant createdAt, Instant updatedAt, Instant receivedAt,
		List<PurchaseOrderItemView> items) {
}
