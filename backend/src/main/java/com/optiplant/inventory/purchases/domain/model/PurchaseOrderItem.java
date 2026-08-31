package com.optiplant.inventory.purchases.domain.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;

/**
 * An immutable line of a {@link PurchaseOrder} (design §3.2). No setters.
 *
 * <p>{@link #effectiveUnitCost()} = {@code unitCost × (1 − discountPercent/100)} at scale 4
 * {@code HALF_UP} — the discount-adjusted acquisition cost (R-17, PA-09). <strong>This expression
 * lives only here</strong> (D-9), never in SQL, so the reception (§5) and the cost history (§6.3)
 * share one authority. {@link #pendingQuantity()} = {@code max(0, ordered − received)}.
 */
public record PurchaseOrderItem(UUID externalId, UUID productExternalId, PurchaseQuantity orderedQuantity,
		BigDecimal receivedQuantity, Money unitCost, DiscountPercent discountPercent, Money subtotal) {

	public PurchaseOrderItem {
		if (externalId == null) {
			throw new IllegalArgumentException("item externalId must not be null");
		}
		if (productExternalId == null) {
			throw new IllegalArgumentException("item productExternalId must not be null");
		}
		if (orderedQuantity == null) {
			throw new IllegalArgumentException("orderedQuantity must not be null");
		}
		if (unitCost == null) {
			throw new IllegalArgumentException("unitCost must not be null");
		}
		if (discountPercent == null) {
			discountPercent = DiscountPercent.ZERO;
		}
		if (subtotal == null) {
			throw new IllegalArgumentException("subtotal must not be null");
		}
		receivedQuantity = receivedQuantity == null ? BigDecimal.ZERO : receivedQuantity;
		receivedQuantity = receivedQuantity.setScale(4, RoundingMode.HALF_UP);
		if (receivedQuantity.signum() < 0) {
			throw new IllegalArgumentException("receivedQuantity must not be negative");
		}
	}

	/** {@code unitCost × (1 − discountPercent/100)}, scale 4 {@code HALF_UP} (R-17, D-9). */
	public Money effectiveUnitCost() {
		return unitCost.multiply(discountPercent.complementFactor());
	}

	/** {@code max(0, orderedQuantity − receivedQuantity)}. */
	public BigDecimal pendingQuantity() {
		return orderedQuantity.value().subtract(receivedQuantity).max(BigDecimal.ZERO);
	}

	/** A copy with {@code receivedQuantity} accumulated by {@code delta}. */
	public PurchaseOrderItem withAdditionalReceived(BigDecimal delta) {
		return new PurchaseOrderItem(externalId, productExternalId, orderedQuantity,
				receivedQuantity.add(delta), unitCost, discountPercent, subtotal);
	}
}
