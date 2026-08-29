package com.optiplant.inventory.inventory.domain.model;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * A non-negative balance-shaped quantity: {@code current_stock}, {@code reserved_stock},
 * {@code in_transit_stock} or {@code min_stock_threshold}. Normalized to scale 4
 * {@code HALF_UP}, matching the {@code NUMERIC(14,4)} columns it mirrors.
 *
 * <p>Enforces {@code CHECK (current_stock >= 0)} and {@code min_stock_threshold >= 0} (R-14) at
 * the domain boundary, before any SQL (T-07). Violations throw {@link IllegalArgumentException},
 * mapped to {@code 400 invalid_request}.
 */
public record StockLevel(BigDecimal value) {

	private static final int SCALE = 4;

	public StockLevel {
		if (value == null) {
			throw new IllegalArgumentException("stock level must not be null");
		}
		value = value.setScale(SCALE, RoundingMode.HALF_UP);
		if (value.signum() < 0) {
			throw new IllegalArgumentException("stock level must not be negative");
		}
	}

	public static StockLevel zero() {
		return new StockLevel(BigDecimal.ZERO);
	}
}
