package com.optiplant.inventory.inventory.domain.model;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * A non-negative unit cost, in the branch's local currency. Normalized to scale 4
 * {@code HALF_UP}, matching {@code kardex_movements.unit_cost NUMERIC(14,4)}.
 *
 * <p>Enforces {@code CHECK (unit_cost >= 0)} at the domain boundary. Violations throw
 * {@link IllegalArgumentException}, mapped to {@code 400 invalid_request}.
 */
public record UnitCost(BigDecimal value) {

	private static final int SCALE = 4;

	public UnitCost {
		if (value == null) {
			throw new IllegalArgumentException("unit cost must not be null");
		}
		value = value.setScale(SCALE, RoundingMode.HALF_UP);
		if (value.signum() < 0) {
			throw new IllegalArgumentException("unit cost must not be negative");
		}
	}
}
