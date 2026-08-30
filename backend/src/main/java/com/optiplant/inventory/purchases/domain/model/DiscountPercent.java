package com.optiplant.inventory.purchases.domain.model;

import com.optiplant.inventory.purchases.domain.exception.DiscountOutOfRangeException;
import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * A line discount percentage in {@code [0, 100]}, scale 2 {@code HALF_UP}. Enforces
 * {@code purchase_order_items.discount_percent NUMERIC(5,2) CHECK (BETWEEN 0 AND 100)} (R-05).
 * Out-of-range values throw {@link DiscountOutOfRangeException} so the error stays
 * {@code discount_out_of_range}, never the generic {@code invalid_request}.
 */
public record DiscountPercent(BigDecimal value) {

	private static final BigDecimal HUNDRED = new BigDecimal("100");

	public static final DiscountPercent ZERO = new DiscountPercent(BigDecimal.ZERO);

	public DiscountPercent {
		if (value == null) {
			throw new IllegalArgumentException("discount percent must not be null");
		}
		if (value.compareTo(BigDecimal.ZERO) < 0 || value.compareTo(HUNDRED) > 0) {
			throw new DiscountOutOfRangeException(value);
		}
		value = value.setScale(2, RoundingMode.HALF_UP);
	}

	public static DiscountPercent of(BigDecimal value) {
		return value == null ? ZERO : new DiscountPercent(value);
	}

	public static DiscountPercent of(String value) {
		return new DiscountPercent(new BigDecimal(value));
	}

	/** The multiplier {@code (1 - value/100)}, scale 6 {@code HALF_UP}, for effective-cost math (R-17). */
	public BigDecimal complementFactor() {
		return BigDecimal.ONE.subtract(value.divide(HUNDRED, 6, RoundingMode.HALF_UP));
	}
}
