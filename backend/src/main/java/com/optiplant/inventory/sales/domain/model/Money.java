package com.optiplant.inventory.sales.domain.model;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Monetary value in sales domain, non-negative, scale 4 {@code HALF_UP}.
 * Enforces every {@code NUMERIC(14,4) CHECK (>= 0)} in sales schema (design §4).
 */
public record Money(BigDecimal value) {

	public static final Money ZERO = new Money(BigDecimal.ZERO);

	public Money {
		if (value == null) {
			throw new IllegalArgumentException("money must not be null");
		}
		value = value.setScale(4, RoundingMode.HALF_UP);
		if (value.compareTo(BigDecimal.ZERO) < 0) {
			throw new IllegalArgumentException("money must be non-negative");
		}
	}

	public static Money of(BigDecimal value) {
		return new Money(value);
	}

	public static Money of(String value) {
		return new Money(new BigDecimal(value));
	}

	public Money add(Money other) {
		return new Money(this.value.add(other.value));
	}

	public Money subtract(Money other) {
		return new Money(this.value.subtract(other.value));
	}
}
