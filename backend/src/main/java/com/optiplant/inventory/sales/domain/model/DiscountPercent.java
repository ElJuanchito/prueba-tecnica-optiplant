package com.optiplant.inventory.sales.domain.model;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Line discount percentage (0..100), scale 2 {@code HALF_UP}.
 * Enforces {@code CHECK (discount_percent BETWEEN 0 AND 100)} and R-13 (design §4).
 */
public record DiscountPercent(BigDecimal value) {

	public static final DiscountPercent ZERO = new DiscountPercent(BigDecimal.ZERO);

	public DiscountPercent {
		if (value == null) {
			throw new IllegalArgumentException("discountPercent must not be null");
		}
		if (value.compareTo(BigDecimal.ZERO) < 0 || value.compareTo(new BigDecimal("100")) > 0) {
			throw new IllegalArgumentException("discountPercent must be between 0 and 100");
		}
		value = value.setScale(2, RoundingMode.HALF_UP);
	}

	public static DiscountPercent of(BigDecimal value) {
		return new DiscountPercent(value);
	}

	public static DiscountPercent of(String value) {
		return new DiscountPercent(new BigDecimal(value));
	}
}
