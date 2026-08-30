package com.optiplant.inventory.purchases.domain.model;

import com.optiplant.inventory.purchases.domain.exception.InvalidOrderQuantityException;
import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * A strictly positive ordered quantity in the product's base unit (RN-13). Normalized to
 * scale 4 {@code HALF_UP}, matching {@code purchase_order_items.ordered_quantity NUMERIC(14,4)}
 * and its {@code CHECK (ordered_quantity > 0)} (R-05, T-07).
 */
public record PurchaseQuantity(BigDecimal value) {

	private static final int SCALE = 4;

	public PurchaseQuantity {
		if (value == null) {
			throw new InvalidOrderQuantityException("ordered quantity must not be null");
		}
		value = value.setScale(SCALE, RoundingMode.HALF_UP);
		if (value.signum() <= 0) {
			throw new InvalidOrderQuantityException("ordered quantity must be strictly positive");
		}
	}

	public static PurchaseQuantity of(BigDecimal value) {
		return new PurchaseQuantity(value);
	}

	public static PurchaseQuantity of(String value) {
		return new PurchaseQuantity(new BigDecimal(value));
	}
}
