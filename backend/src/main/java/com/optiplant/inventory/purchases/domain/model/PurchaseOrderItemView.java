package com.optiplant.inventory.purchases.domain.model;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Enriched read view of a {@link PurchaseOrderItem} for {@link PurchaseOrderDetail} (contract §6):
 * the stored line plus the product's {@code sku} / {@code name}, its derived {@code pendingQuantity}
 * and its {@code effectiveUnitCost} (R-17, R-27).
 */
public record PurchaseOrderItemView(UUID externalId, UUID productExternalId, String sku, String name,
		BigDecimal orderedQuantity, BigDecimal receivedQuantity, BigDecimal pendingQuantity, BigDecimal unitCost,
		BigDecimal discountPercent, BigDecimal effectiveUnitCost, BigDecimal subtotal) {
}
