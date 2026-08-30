package com.optiplant.inventory.pricing.domain.service;

import com.optiplant.inventory.pricing.domain.exception.DiscountCapExceededException;
import com.optiplant.inventory.pricing.domain.model.DiscountCap;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;

/**
 * Pure calculator for pricing quotes (CU-VEN-02, design §3, D-6).
 *
 * <p>Per line:
 * <ul>
 *   <li>{@code unitPrice = listUnitPrice * (1 - discountPercent/100)}, scale 4 HALF_UP</li>
 *   <li>{@code subtotal = quantity * unitPrice}, scale 4 HALF_UP</li>
 * </ul>
 * after {@link DiscountCap} validation.
 */
public final class QuoteCalculator {

	private static final BigDecimal HUNDRED = new BigDecimal("100");

	private QuoteCalculator() {
	}

	public record QuoteItemCalculation(
			UUID productExternalId,
			BigDecimal quantity,
			BigDecimal discountPercent,
			BigDecimal listUnitPrice,
			BigDecimal unitPrice,
			BigDecimal subtotal
	) {
	}

	public static QuoteItemCalculation calculateLine(
			UUID productExternalId,
			BigDecimal quantity,
			BigDecimal discountPercent,
			BigDecimal listUnitPrice,
			DiscountCap maxDiscountPercent
	) {
		if (productExternalId == null) {
			throw new IllegalArgumentException("productExternalId must not be null");
		}
		if (quantity == null || quantity.compareTo(BigDecimal.ZERO) <= 0) {
			throw new IllegalArgumentException("quantity must be greater than zero");
		}
		if (listUnitPrice == null || listUnitPrice.compareTo(BigDecimal.ZERO) < 0) {
			throw new IllegalArgumentException("listUnitPrice must be non-negative");
		}
		BigDecimal effectiveDiscount = discountPercent == null ? BigDecimal.ZERO : discountPercent.setScale(2, RoundingMode.HALF_UP);
		if (effectiveDiscount.compareTo(BigDecimal.ZERO) < 0 || effectiveDiscount.compareTo(HUNDRED) > 0) {
			throw new IllegalArgumentException("discountPercent must be between 0 and 100");
		}
		if (maxDiscountPercent != null && effectiveDiscount.compareTo(maxDiscountPercent.value()) > 0) {
			throw new DiscountCapExceededException(effectiveDiscount, maxDiscountPercent.value());
		}

		BigDecimal factor = BigDecimal.ONE.subtract(effectiveDiscount.divide(HUNDRED, 6, RoundingMode.HALF_UP));
		BigDecimal unitPrice = listUnitPrice.multiply(factor).setScale(4, RoundingMode.HALF_UP);
		BigDecimal subtotal = quantity.multiply(unitPrice).setScale(4, RoundingMode.HALF_UP);

		return new QuoteItemCalculation(
				productExternalId,
				quantity,
				effectiveDiscount,
				listUnitPrice.setScale(4, RoundingMode.HALF_UP),
				unitPrice,
				subtotal
		);
	}
}
