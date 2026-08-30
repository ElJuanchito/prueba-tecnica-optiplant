package com.optiplant.inventory.pricing.domain.model;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * The maximum discount percentage allowed under a price list (0..100).
 * Enforces {@code CHECK (max_discount_percent BETWEEN 0 AND 100)} and RN-17.
 */
public record DiscountCap(BigDecimal value) {

	private static final BigDecimal MIN = BigDecimal.ZERO;
	private static final BigDecimal MAX = new BigDecimal("100");

	public DiscountCap {
		if (value == null) {
			throw new IllegalArgumentException("maxDiscountPercent must not be null");
		}
		if (value.compareTo(MIN) < 0 || value.compareTo(MAX) > 0) {
			throw new IllegalArgumentException("maxDiscountPercent must be between 0 and 100");
		}
		value = value.setScale(2, RoundingMode.HALF_UP);
	}

	public static DiscountCap of(BigDecimal value) {
		return new DiscountCap(value);
	}

	public static DiscountCap of(String value) {
		return new DiscountCap(new BigDecimal(value));
	}
}
