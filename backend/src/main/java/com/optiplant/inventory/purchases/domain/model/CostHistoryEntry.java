package com.optiplant.inventory.purchases.domain.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * One row of the per-product agreed-cost history (contract §6, R-26). {@code effectiveUnitCost} is
 * derived through {@link PurchaseOrderItem#effectiveUnitCost()} (D-9), never from SQL. It never
 * exposes {@code branch_inventories.average_cost}.
 */
public record CostHistoryEntry(UUID orderExternalId, String orderNumber, SupplierRef supplier, BigDecimal unitCost,
		BigDecimal discountPercent, BigDecimal effectiveUnitCost, BigDecimal quantity, Instant orderedAt,
		Instant receivedAt) {
}
