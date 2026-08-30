package com.optiplant.inventory.sales.domain.model;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Sale tax percentage (0..100), scale 2 {@code HALF_UP}.
 * Enforces F-4 / PA-06 (design §4).
 */
public record TaxPercent(BigDecimal value) {

	public static final TaxPercent ZERO = new TaxPercent(BigDecimal.ZERO);

	public TaxPercent {
		if (value == null) {
			throw new IllegalArgumentException("taxPercent must not be null");
		}
		if (value.compareTo(BigDecimal.ZERO) < 0 || value.compareTo(new BigDecimal("100")) > 0) {
			throw new IllegalArgumentException("taxPercent must be between 0 and 100");
		}
		value = value.setScale(2, RoundingMode.HALF_UP);
	}

	public static TaxPercent of(BigDecimal value) {
		return new TaxPercent(value);
	}

	public static TaxPercent of(String value) {
		return new TaxPercent(new BigDecimal(value));
	}
}
