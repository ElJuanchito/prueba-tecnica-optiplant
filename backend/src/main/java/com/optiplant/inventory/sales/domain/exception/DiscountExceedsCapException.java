package com.optiplant.inventory.sales.domain.exception;

import java.math.BigDecimal;

/**
 * Thrown when a line discount exceeds the applied price list's cap (R-13, RN-17).
 * Maps to {@code 400 discount_exceeds_cap}.
 */
public class DiscountExceedsCapException extends RuntimeException {

	private final BigDecimal requestedDiscount;
	private final BigDecimal maxDiscountPercent;

	public DiscountExceedsCapException(BigDecimal requestedDiscount, BigDecimal maxDiscountPercent) {
		super("Requested discount " + requestedDiscount + "% exceeds maximum allowed cap of " + maxDiscountPercent + "%");
		this.requestedDiscount = requestedDiscount;
		this.maxDiscountPercent = maxDiscountPercent;
	}

	public BigDecimal requestedDiscount() {
		return requestedDiscount;
	}

	public BigDecimal maxDiscountPercent() {
		return maxDiscountPercent;
	}
}
