package com.optiplant.inventory.inventory.domain.model;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * A strictly positive movement quantity, in the product's base unit (RN-13). Normalized to
 * scale 4 {@code HALF_UP} — matching {@code kardex_movements.quantity NUMERIC(14,4)}, so no
 * rounding surprise is deferred to the database.
 *
 * <p>The sign is a property of {@code StockMovementType}, never of this value (P-02) — this type
 * enforces {@code CHECK (quantity > 0)} at the domain boundary, before any SQL.
 *
 * <p>Violations throw {@link IllegalArgumentException}, which the web layer maps to
 * {@code 400 invalid_request}.
 */
public record Quantity(BigDecimal value) {

	private static final int SCALE = 4;

	public Quantity {
		if (value == null) {
			throw new IllegalArgumentException("quantity must not be null");
		}
		value = value.setScale(SCALE, RoundingMode.HALF_UP);
		if (value.signum() <= 0) {
			throw new IllegalArgumentException("quantity must be strictly positive");
		}
	}
}
