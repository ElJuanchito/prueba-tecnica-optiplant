package com.optiplant.inventory.sales.domain.service;

import com.optiplant.inventory.sales.domain.exception.DiscountExceedsCapException;
import java.math.BigDecimal;

/**
 * Validates that line discounts do not exceed the applied price list's maximum cap (R-13, RN-17, design §4.1).
 *
 * <p>Enforced identically for every role (F-5, PA-02).
 */
public final class DiscountCapPolicy {

	private DiscountCapPolicy() {
	}

	/**
	 * Validates the requested discount against the list's cap.
	 *
	 * @param requestedDiscount  the requested line discount percentage (0..100)
	 * @param maxDiscountPercent the maximum discount percentage allowed by the price list
	 * @throws DiscountExceedsCapException if {@code requestedDiscount > maxDiscountPercent}
	 */
	public static void validate(BigDecimal requestedDiscount, BigDecimal maxDiscountPercent) {
		if (requestedDiscount == null) {
			return;
		}
		if (maxDiscountPercent == null) {
			throw new IllegalArgumentException("maxDiscountPercent must not be null");
		}
		if (requestedDiscount.compareTo(BigDecimal.ZERO) < 0 || requestedDiscount.compareTo(new BigDecimal("100")) > 0) {
			throw new IllegalArgumentException("discountPercent must be between 0 and 100");
		}
		if (requestedDiscount.compareTo(maxDiscountPercent) > 0) {
			throw new DiscountExceedsCapException(requestedDiscount, maxDiscountPercent);
		}
	}
}
