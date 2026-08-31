package com.optiplant.inventory.purchases.domain.exception;

import java.math.BigDecimal;

/**
 * {@code discount_percent} outside {@code [0, 100]} (R-05). Maps to
 * {@code 400 discount_out_of_range}.
 */
public class DiscountOutOfRangeException extends RuntimeException {

	public DiscountOutOfRangeException(BigDecimal value) {
		super("Discount percent must be between 0 and 100, was: " + value);
	}

	public DiscountOutOfRangeException(String message) {
		super(message);
	}
}
