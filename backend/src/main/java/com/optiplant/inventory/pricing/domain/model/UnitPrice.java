package com.optiplant.inventory.pricing.domain.model;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * A price list item's unit price.
 * Enforces {@code CHECK (unit_price >= 0)} at scale 4 (NUMERIC(14,4)).
 */
public record UnitPrice(BigDecimal value) {

	public UnitPrice {
		if (value == null) {
			throw new IllegalArgumentException("unitPrice must not be null");
		}
		if (value.compareTo(BigDecimal.ZERO) < 0) {
			throw new IllegalArgumentException("unitPrice must be greater than or equal to 0");
		}
		value = value.setScale(4, RoundingMode.HALF_UP);
	}

	public static UnitPrice of(BigDecimal value) {
		return new UnitPrice(value);
	}

	public static UnitPrice of(String value) {
		return new UnitPrice(new BigDecimal(value));
	}
}
